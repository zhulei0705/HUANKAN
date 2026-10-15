package cn.huankan.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StreamService {
    private final JdbcTemplate db; private final ObjectMapper json;
    private final Map<SseEmitter,Long> clients=new ConcurrentHashMap<>();
    private final Map<SseEmitter,java.util.UUID> connections=new ConcurrentHashMap<>();
    private final Map<SseEmitter,java.util.Set<String>> sourceFilters=new ConcurrentHashMap<>();
    private final Map<SseEmitter,Integer> priorities=new ConcurrentHashMap<>();
    private final Map<SseEmitter,String> scopes=new ConcurrentHashMap<>();
    private final Map<SseEmitter,String> watchOwners=new ConcurrentHashMap<>();
    public void bindWatch(SseEmitter emitter,String owner){watchOwners.put(emitter,owner);db.update("UPDATE stream_connection SET watch_owner_key=? WHERE id=?",owner,connections.get(emitter));}
    private final Map<SseEmitter,Long> replayCeilings=new ConcurrentHashMap<>();
    public int activeClients(){return clients.size();}
    public java.util.UUID connectionId(SseEmitter emitter){return connections.get(emitter);}
    protected SseEmitter createEmitter(){return new SseEmitter(300000L);}
    public void clientClose(java.util.UUID id,String kind){
        if(!java.util.Set.of("CLIENT_REQUESTED","BROWSER_CLOSED").contains(kind))throw new IllegalArgumentException("关闭原因无效");
        db.update("UPDATE stream_connection SET closed_at=now(),close_kind=? WHERE id=? AND closed_at IS NULL",kind,id);
        connections.forEach((emitter,connection)->{if(connection.equals(id)){clients.remove(emitter);connections.remove(emitter);replayCeilings.remove(emitter);watchOwners.remove(emitter);scopes.remove(emitter);sourceFilters.remove(emitter);priorities.remove(emitter);emitter.complete();}});
    }
    private void close(SseEmitter emitter,String kind){var id=connections.remove(emitter);clients.remove(emitter);replayCeilings.remove(emitter);watchOwners.remove(emitter);scopes.remove(emitter);sourceFilters.remove(emitter);priorities.remove(emitter);if(id!=null)db.update("UPDATE stream_connection SET closed_at=now(),close_kind=COALESCE(close_kind,?) WHERE id=? AND closed_at IS NULL",kind,id);}
    public void acknowledge(java.util.UUID delivery,java.time.Instant received) {
        if(received==null || received.isAfter(java.time.Instant.now().plusSeconds(300)))throw new IllegalArgumentException("客户端确认时间无效");
        int count=db.update("UPDATE stream_delivery SET client_received_at=?,acknowledged_at=now() WHERE id=? AND outcome='SENT' AND acknowledged_at IS NULL",java.sql.Timestamp.from(received),delivery);
        if(count==0 && !Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM stream_delivery WHERE id=? AND outcome='SENT')",Boolean.class,delivery)))throw new IllegalArgumentException("发送记录不存在");
    }
    public StreamService(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}
    public long cursor() { return db.queryForObject("SELECT greatest((SELECT COALESCE(max(id),0) FROM stream_outbox),(SELECT COALESCE(max(id),0) FROM notification_dispatch))",Long.class); }
    @org.springframework.context.event.EventListener(org.springframework.context.event.ContextClosedEvent.class)
    public void closeClients(){clients.keySet().forEach(e->{close(e,"SERVER_SHUTDOWN");e.complete();});}
    public SseEmitter connect(long cursor) {return connect(cursor,"HISTORY");}
    public SseEmitter connect(long cursor,String scope){return connect(cursor,scope,"",0);}
    public SseEmitter connect(long cursor,String scope,String sourceList,int minimumPriority) {return connect(cursor,scope,sourceList,minimumPriority,"");}
    public synchronized SseEmitter connect(long cursor,String scope,String sourceList,int minimumPriority,String watchOwner) {
        if(minimumPriority<0||minimumPriority>100||sourceList==null||sourceList.length()>200)throw new IllegalArgumentException("订阅优先级或来源无效");
        var selectedSources=new java.util.HashSet<String>();if(!sourceList.isBlank())for(String source:sourceList.split(",")){if(!source.matches("[A-Z_]{2,30}")||!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM source_registry WHERE id=?)",Boolean.class,source)))throw new IllegalArgumentException("订阅来源无效");selectedSources.add(source);}
        if(!java.util.Set.of("OFFICIAL","LEADS","HISTORY").contains(scope))throw new IllegalArgumentException("订阅范围无效");
        if(cursor<0) throw new IllegalArgumentException("消息游标无效");
        if(clients.size()>=50) throw new IllegalStateException("推送连接达到上限");
        var emitter=createEmitter();
        if(!watchOwner.isEmpty())watchOwners.put(emitter,watchOwner);scopes.put(emitter,scope);sourceFilters.put(emitter,selectedSources);priorities.put(emitter,minimumPriority);
        var connection=java.util.UUID.randomUUID();long ceiling=cursor();connections.put(emitter,connection);replayCeilings.put(emitter,ceiling);
        db.update("INSERT INTO stream_connection(id,initial_cursor,replay_ceiling,subscription_scope,subscribed_sources,minimum_priority) VALUES(?,?,?,?,string_to_array(?,','),?)",connection,cursor,ceiling,scope,sourceList,minimumPriority);
        clients.put(emitter,cursor);
        if(!watchOwner.isEmpty())db.update("UPDATE stream_connection SET watch_owner_key=? WHERE id=?",watchOwner,connection);
        emitter.onCompletion(() -> close(emitter,"DISCONNECT_UNCLASSIFIED"));
        emitter.onTimeout(() -> {close(emitter,"SERVER_TIMEOUT");emitter.complete();});
        emitter.onError(e -> close(emitter,"NETWORK_OR_CLIENT_DISCONNECT"));
        // Flush the real connection immediately; collection scheduling must not delay headers.
        try {emitter.send(SseEmitter.event().comment("实时连接已建立"));}
        catch(IOException e){close(emitter,"SERVER_SEND_EXCEPTION");throw new IllegalStateException("推送连接初始化失败",e);}
        return emitter;
    }
    @Scheduled(fixedDelay=1000) public void publish() {
        long ceiling;
        try{ceiling=cursor();}catch(org.springframework.dao.DataAccessException failure){cn.huankan.reliability.JdbcDiagnostics.failure("SSE发布调度/读取游标","SCHEDULER_DATABASE_UNAVAILABLE",db,failure);throw failure;}
        clients.forEach((emitter,cursor) -> {
            try {
                var rows=db.queryForList("SELECT * FROM stream_journal WHERE id>? AND id<=? AND (?='HISTORY' OR official_eligible OR ?='LEADS' AND source_id='GDELT') AND (?='' OR source_id=ANY(string_to_array(?,','))) AND (priority>=? OR NOT qualification_ready) AND EXISTS(SELECT 1 FROM source_license l WHERE l.source_id=stream_journal.source_id AND l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now())) AND (?='' OR watch_matches(?,evidence_id,event_id,source_id,CASE WHEN source_id='GDELT' THEN 'L2' ELSE 'L0' END,CASE WHEN NOT qualification_ready AND (notification_kind<>'HISTORICAL_BACKFILL' OR source_id='GDELT') THEN 100 ELSE priority END)) ORDER BY id LIMIT 100",cursor,ceiling,scopes.getOrDefault(emitter,"OFFICIAL"),scopes.getOrDefault(emitter,"OFFICIAL"),String.join(",",sourceFilters.getOrDefault(emitter,java.util.Set.of())),String.join(",",sourceFilters.getOrDefault(emitter,java.util.Set.of())),priorities.getOrDefault(emitter,0),watchOwners.getOrDefault(emitter,""),watchOwners.getOrDefault(emitter,""));
                boolean waiting=false;
                if(priorities.getOrDefault(emitter,0)>0||watchOwners.containsKey(emitter)){for(int i=0;i<rows.size();i++){var r=rows.get(i);String scope=scopes.getOrDefault(emitter,"OFFICIAL");boolean selected=sourceFilters.getOrDefault(emitter,java.util.Set.of()).isEmpty()||sourceFilters.get(emitter).contains(r.get("source_id"));boolean inScope="HISTORY".equals(scope)||Boolean.TRUE.equals(r.get("official_eligible"))||"LEADS".equals(scope)&&"GDELT".equals(r.get("source_id"));if(selected&&inScope&&!Boolean.TRUE.equals(r.get("qualification_ready"))&&(!"HISTORICAL_BACKFILL".equals(r.get("notification_kind"))||"GDELT".equals(r.get("source_id")))){rows=rows.subList(0,i);waiting=true;break;}}}
                int sent=0;var classes=new java.util.TreeMap<String,Integer>();
                for(var row:rows) {
                    long id=((Number)row.get("id")).longValue();
                    classes.merge(row.get("message_class").toString(),1,Integer::sum);
                    String scope=scopes.getOrDefault(emitter,"OFFICIAL");
                    boolean eligible="HISTORY".equals(scope)||Boolean.TRUE.equals(row.get("official_eligible"))||"LEADS".equals(scope)&&"GDELT".equals(row.get("source_id"));
                    eligible=eligible&&(sourceFilters.getOrDefault(emitter,java.util.Set.of()).isEmpty()||sourceFilters.get(emitter).contains(row.get("source_id")))&&((Number)row.get("priority")).intValue()>=priorities.getOrDefault(emitter,0);
                    if(!eligible)continue;
                    row.put("topics",json.readTree(row.get("topics").toString()));row.put("subscription_scope",scope);row.put("notification_eligible",true);
                    var delivery=java.util.UUID.randomUUID();
                    var deliveryConnection=connections.get(emitter);
                    if(deliveryConnection==null)return; // A concurrently closed subscriber is no longer active.
                    boolean replay=id<=replayCeilings.getOrDefault(emitter,0L);String type=Boolean.TRUE.equals(row.get("qualification_update"))?"QUALIFICATION":"HISTORICAL_BACKFILL".equals(row.get("notification_kind"))?"BACKFILL":replay?"REPLAY":"LIVE";row.put("replay_flag",replay);row.put("delivery_type",type);row.put("delivery_id",delivery.toString());
                    row.put("connection_id",deliveryConnection);
                    row.put("server_sent_at",java.time.Instant.now().toString());
                    emitter.send(SseEmitter.event().id(Long.toString(id)).name("event").data(json.writeValueAsString(row)));
                    db.update("INSERT INTO stream_delivery(id,connection_id,outbox_id,sent_at,outcome,replay,delivery_type) VALUES(?,?,?,?,'SENT',?,?)",delivery,deliveryConnection,row.get("source_outbox_id"),java.sql.Timestamp.from(java.time.Instant.now()),replay,type);
                    sent++;clients.computeIfPresent(emitter,(key,previous)->id);
                }
                long advance=waiting?(rows.isEmpty()?cursor:((Number)rows.getLast().get("id")).longValue()):rows.size()<100?ceiling:((Number)rows.getLast().get("id")).longValue();
                if(connections.containsKey(emitter)&&advance>cursor){var audit=db.queryForMap("SELECT min(source_outbox_id) first_source,max(source_outbox_id) last_source,min(id) first_cursor,count(*) scanned,COALESCE((SELECT jsonb_object_agg(message_class,n) FROM(SELECT message_class,count(*) n FROM stream_journal WHERE id>? AND id<=? GROUP BY message_class)x),'{}'::jsonb) classes FROM stream_journal WHERE id>? AND id<=?",cursor,advance,cursor,advance);int scanned=((Number)audit.get("scanned")).intValue();
                    if(clients.getOrDefault(emitter,0L)<advance)emitter.send(SseEmitter.event().id(Long.toString(advance)).name("checkpoint").data(json.writeValueAsString(Map.of("checkpoint",true,"connection_id",connections.get(emitter),"scanned",scanned,"sent",sent,"excluded",scanned-sent,"classes",json.readTree(audit.get("classes").toString())))));
                    if(scanned>0)db.update("INSERT INTO stream_filter_batch(connection_id,first_outbox,last_outbox,scanned,sent,excluded,classes,first_cursor,last_cursor) VALUES(?,?,?,?,?,?,?::jsonb,?,?)",connections.get(emitter),audit.get("first_source"),audit.get("last_source"),scanned,sent,scanned-sent,audit.get("classes").toString(),audit.get("first_cursor"),advance);clients.computeIfPresent(emitter,(key,previous)->advance);
                }else emitter.send(SseEmitter.event().comment(waiting?"等待消息资格分类":"保持连接"));
                if(connections.get(emitter)!=null)db.update("UPDATE stream_connection SET last_activity_at=now() WHERE id=?",connections.get(emitter));
            } catch(Exception e) {
                var connection=connections.remove(emitter);
                String kind=e instanceof org.springframework.dao.DataAccessException?"OUTBOX_DELIVERY_FAILURE":e instanceof IOException||e instanceof IllegalStateException?"NETWORK_OR_CLIENT_DISCONNECT":"SERVER_SEND_EXCEPTION";
                if(connection!=null){db.update("INSERT INTO stream_delivery(id,connection_id,sent_at,outcome,failure_kind) VALUES(?,?,now(),'FAILED',?)",java.util.UUID.randomUUID(),connection,kind);db.update("UPDATE stream_connection SET closed_at=now(),close_kind=COALESCE(close_kind,?) WHERE id=? AND closed_at IS NULL",kind,connection);}
                clients.remove(emitter);replayCeilings.remove(emitter);watchOwners.remove(emitter);scopes.remove(emitter);sourceFilters.remove(emitter);priorities.remove(emitter);emitter.completeWithError(e);
            }
        });
    }
}
