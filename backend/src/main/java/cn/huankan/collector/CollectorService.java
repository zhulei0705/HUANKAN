package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CollectorService {
    private final JdbcTemplate db; private final OfficialHttpClient http; private final EvidenceService evidence;
    private final boolean enabled; private final SecConfiguration sec;
    @org.springframework.beans.factory.annotation.Autowired private GdeltCollector gdelt;
    @org.springframework.beans.factory.annotation.Autowired private FredCollector fred;
    @org.springframework.beans.factory.annotation.Autowired private NbsCollector nbs;
    @org.springframework.beans.factory.annotation.Autowired private EcbDataCollector ecbData;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CollectorGuard guard;
    public CollectorService(JdbcTemplate db,OfficialHttpClient http,EvidenceService evidence,@Value("${huankan.collector-enabled}") boolean enabled,SecConfiguration sec) { this.db=db;this.http=http;this.evidence=evidence;this.enabled=enabled;this.sec=sec; }
    @Scheduled(fixedDelay=60000,initialDelay=10000) public void scheduled() {
        if(!enabled) return;
        long scheduledStart=System.nanoTime();
        org.slf4j.LoggerFactory.getLogger(getClass()).info("采集调度开始：SCHEDULER_STARTED，实际时间{}",Instant.now());
        try{
        for(var row:db.queryForList("SELECT s.id FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) AND NOT EXISTS(SELECT 1 FROM ingestion_job j WHERE j.source_id=s.id AND j.started_at>now()-s.interval_seconds*interval '1 second')")){try{collect(row.get("id").toString());}catch(Exception e){cn.huankan.reliability.JdbcDiagnostics.failure("采集来源/"+row.get("id"),"TASK_NOT_COMPLETED",db,e);}}
        }catch(org.springframework.dao.DataAccessException failure){cn.huankan.reliability.JdbcDiagnostics.failure("采集调度/候选查询","SCHEDULER_DATABASE_UNAVAILABLE",db,failure);throw failure;}
        finally{org.slf4j.LoggerFactory.getLogger(getClass()).info("采集调度本轮结束：SCHEDULER_ROUND_FINISHED，耗时{}毫秒；不是全部任务成功声明",(System.nanoTime()-scheduledStart)/1000000);}
    }
    public void collect(String source) {collect(source,false);}
    public synchronized void collect(String source,boolean force) {
        if(source.equals("GDELT")){gdelt.collect();return;}
        if(source.equals("FRED")){fred.collect();return;}
        if(source.equals("NBS")){nbs.collect();return;}
        if(source.equals("ECB_DATA")){ecbData.collect();return;}
        if(guard==null){run(source,force);return;}
        try(var lease=guard.acquire(source)){if(lease!=null)run(source,force);}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("来源采集锁异常",e);}
    }
    private void run(String source,boolean force) {
        if(!java.util.Set.of("FED","ECB","SEC").contains(source)) throw new IllegalArgumentException("连接器尚未实现");
        if(source.equals("SEC")&&!sec.enabled())throw new IllegalStateException(sec.state());
        Boolean permitted=db.queryForObject("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON s.id=l.source_id WHERE s.id=?",Boolean.class,source);
        if(!Boolean.TRUE.equals(permitted)) throw new IllegalStateException("来源未启用或许可待核验");
        String ciks=sec.followedCiks();
        if(source.equals("SEC")&&ciks.isBlank())ciks=String.join(",",db.queryForList("SELECT cik FROM sec_followed_company ORDER BY cik",String.class));
        if(source.equals("SEC") && (!sec.hasContact() || !ciks.matches("[0-9]{1,10}(,[0-9]{1,10})*"))) throw new IllegalStateException("SEC需要真实联系邮箱和CIK关注名单");
        UUID job=UUID.randomUUID(); long start=System.nanoTime();
        db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,?,'RUNNING')",job,source);
        db.update("INSERT INTO audit_log(actor,action,target) VALUES('local-admin-or-scheduler','COLLECT',?)",source);
        int count=0; Integer status=null;
        try {
            String[] endpoints=source.equals("FED")?new String[]{"https://www.federalreserve.gov/feeds/press_all.xml"}:source.equals("ECB")?new String[]{"https://www.ecb.europa.eu/rss/press.html","https://www.ecb.europa.eu/rss/statpress.html"}:java.util.Arrays.stream(ciks.split(",")).map(c -> "https://data.sec.gov/submissions/CIK"+String.format("%010d",Long.parseLong(c))+".json").toArray(String[]::new);
            for(String endpoint:endpoints) {
                var headers=new HashMap<String,String>();
                if(source.equals("FED")&&!force) for(var cursor:db.queryForList("SELECT etag,last_modified FROM source_cursor WHERE source_id=?",source)) {
                    if(cursor.get("etag")!=null) headers.put("If-None-Match",cursor.get("etag").toString());
                    if(cursor.get("last_modified")!=null) headers.put("If-Modified-Since",cursor.get("last_modified").toString());
                }
                var result=http.get(URI.create(endpoint),source.equals("SEC")?sec.userAgent():"HUANKAN/0.1 (local RSS reader)",headers); status=result.status();
                if(status==304) {
                    db.update("UPDATE source_cursor SET last_checked_at=now() WHERE source_id=?",source);
                    continue;
                }
                if(status!=200) throw new IllegalStateException("HTTP_"+status);
                SourceAdapter parser=source.equals("FED")?new FedAdapter():source.equals("ECB")?new RssAdapter("www.ecb.europa.eu"):new SecAdapter();
                var items=parser.parse(result.body());
                int inserted=evidence.persist(source,job,result.body(),items,Instant.now());count+=inserted;
                if(source.equals("SEC")&&!items.isEmpty()){
                    var latest=items.stream().filter(i->i.publishedAt()!=null).max(java.util.Comparator.comparing(SourceAdapter.Item::publishedAt));
                    if(latest.isPresent())db.update("INSERT INTO sec_filing_cursor(cik,last_accession,last_accepted_at) VALUES(?,?,?) ON CONFLICT(cik) DO UPDATE SET last_accession=CASE WHEN excluded.last_accepted_at>=sec_filing_cursor.last_accepted_at THEN excluded.last_accession ELSE sec_filing_cursor.last_accession END,last_accepted_at=greatest(sec_filing_cursor.last_accepted_at,excluded.last_accepted_at),checked_at=now()",endpoint.substring(endpoint.length()-15,endpoint.length()-5),latest.get().documentId(),java.sql.Timestamp.from(latest.get().publishedAt()));
                }
                db.update("UPDATE ingestion_job SET fetched_count=fetched_count+?,duplicate_count=duplicate_count+? WHERE id=?",items.size(),items.size()-inserted,job);
                if(source.equals("FED")) db.update("INSERT INTO source_cursor(source_id,etag,last_modified,last_checked_at,monitoring_started_at) VALUES(?,?,?,now(),now()) ON CONFLICT(source_id) DO UPDATE SET etag=excluded.etag,last_modified=excluded.last_modified,last_checked_at=now(),monitoring_started_at=COALESCE(source_cursor.monitoring_started_at,excluded.monitoring_started_at)",source,result.etag(),result.modified());
            }
            db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now(),http_status=?,new_count=?,duration_ms=? WHERE id=?",status,count,(System.nanoTime()-start)/1000000,job);
            db.update("INSERT INTO source_health_history(source_id,job_id,status) VALUES(?,?,'SUCCESS')",source,job);
            org.slf4j.LoggerFactory.getLogger(getClass()).info("采集任务{}来源{}实际成功：TASK_SUCCESS，新增{}，耗时{}毫秒",job,source,count,(System.nanoTime()-start)/1000000);
        } catch(Exception e) {
            String code=CollectorGuard.failure(e);
            db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),http_status=?,new_count=?,error_code=?,error_detail=?,duration_ms=?,quarantine_count=1 WHERE id=?",status,count,code,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage().substring(0,Math.min(e.getMessage().length(),500)),(System.nanoTime()-start)/1000000,job);
            db.update("INSERT INTO ingestion_quarantine(id,job_id,reason) VALUES(?,?,?)",UUID.randomUUID(),job,code);
            db.update("UPDATE ingestion_quarantine SET anomaly_level=? WHERE job_id=?",code.equals("INTEGRITY_FAILURE")?"EVIDENCE":code.equals("PARSE_FAILURE")?"PARSE":"REQUEST",job);
            db.update("INSERT INTO source_health_history(source_id,job_id,status) VALUES(?,?,'FAILED')",source,job);
            cn.huankan.reliability.JdbcDiagnostics.failure("采集任务/"+source+"/"+job,"TASK_FAILED/"+code,db,e);
        }
    }
}
