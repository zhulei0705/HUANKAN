package cn.huankan.collector;
import cn.huankan.evidence.EvidenceService;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FredCollector {
    private final JdbcTemplate db;private final FredConfiguration config;private final CollectorGuard guard;private final EvidenceService evidence;private final TransactionTemplate tx;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();private final ObjectMapper json=new ObjectMapper();
    public FredCollector(JdbcTemplate db,FredConfiguration config,CollectorGuard guard,EvidenceService evidence,org.springframework.transaction.PlatformTransactionManager manager){this.db=db;this.config=config;this.guard=guard;this.evidence=evidence;tx=new TransactionTemplate(manager);}
    public Map<String,Object> status(){var result=new HashMap<String,Object>();result.put("state",config.state());result.put("key_configured",config.keyConfigured());result.put("series",config.whitelist());result.put("enabled",config.ready());result.putAll(db.queryForMap("SELECT (SELECT count(*) FROM http_request_observation WHERE source_id='FRED') requests,(SELECT status FROM http_request_observation WHERE source_id='FRED' ORDER BY attempted_at DESC LIMIT 1) actual_http_status,(SELECT count(*) FROM raw_evidence WHERE source_id='FRED') evidence,(SELECT count(*) FROM fred_observation) observations"));return result;}
    public void collect(){if(!config.ready())throw new IllegalStateException(config.state());try(var lock=guard.acquire("FRED")){if(lock==null)return;run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("FRED任务异常",e);}}
    private void run()throws Exception{
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='FRED'",Boolean.class)))throw new IllegalStateException("FRED序列内容许可尚未核验");
        UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FRED','RUNNING')",job);int[] counts={0,0};
        try{for(String series:config.whitelist()){
            byte[] meta=fetch("series",series);JsonNode info=json.readTree(meta).path("seriess");if(!info.isArray()||info.size()!=1||!series.equals(info.get(0).path("id").asText()))throw new IllegalArgumentException("FRED序列身份不符");String title=info.get(0).path("title").asText(),units=info.get(0).path("units").asText();
            db.update("INSERT INTO fred_series(series_id,title,units,raw_response,sha256) VALUES(?,?,?,?,?) ON CONFLICT(series_id) DO UPDATE SET title=excluded.title,units=excluded.units,raw_response=excluded.raw_response,sha256=excluded.sha256,acquired_at=now()",series,title,units,meta,EvidenceService.hash(meta));
            byte[] raw=fetch("series/observations",series);JsonNode root=json.readTree(raw),rows=root.path("observations");if(!rows.isArray()||root.path("count").asInt()>1000||rows.size()>1000)throw new IllegalArgumentException("FRED序列历史超过本地1000条预算，需单独分页计划");Instant acquired=Instant.now();
            tx.executeWithoutResult(s->{for(JsonNode row:rows){String date=row.path("date").asText(),value=row.path("value").asText(),start=row.path("realtime_start").asText(),end=row.path("realtime_end").asText();LocalDate.parse(date);LocalDate.parse(start);LocalDate.parse(end);java.math.BigDecimal number=value.equals(".")?null:new java.math.BigDecimal(value);String document=series+":"+date;
                int n=evidence.persist("FRED",job,raw,List.of(new SourceAdapter.Item(document,title+" · "+date+" · "+value+" "+units+" · 实时版本区间起始 "+start,"https://fred.stlouisfed.org/series/"+series,null,"经济统计")),acquired);counts[0]+=n;counts[1]++;
                if(n>0){UUID id=db.queryForObject("SELECT evidence_id FROM financial_event WHERE source_id='FRED' AND document_id=?",UUID.class,document);db.update("INSERT INTO fred_observation(evidence_id,series_id,series_title,units,observation_date,value,missing,realtime_start,realtime_end) VALUES(?,?,?,?,?::date,?,?,?::date,?::date)",id,series,title,units,date,number,number==null,start,end);}
            }});
        }db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now(),new_count=?,fetched_count=?,duplicate_count=? WHERE id=?",counts[0],counts[1],counts[1]-counts[0],job);
        }catch(Exception e){db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),error_code=?,error_detail=? WHERE id=?",CollectorGuard.failure(e),e.getClass().getSimpleName(),job);throw e;}
    }
    private byte[] fetch(String path,String series)throws Exception{
        guard.request("api.stlouisfed.org");String visible="https://api.stlouisfed.org/fred/"+path+"?series_id="+series;
        String url=visible+"&file_type=json&api_key="+config.key()+(path.endsWith("observations")?"&limit=1000":"");Instant started=Instant.now();Integer status=null;String error=null;byte[] bytes=null;
        try{var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());status=response.statusCode();try(var input=response.body()){bytes=input.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)throw new IllegalArgumentException("FRED响应超过4MiB");}if(status==429)guard.cooldown("api.stlouisfed.org",Instant.now().plusSeconds(300).toEpochMilli());if(status!=200)throw new IllegalStateException("HTTP_"+status);return bytes;}
        catch(Exception e){error=CollectorGuard.failure(e);throw new IllegalStateException(error+(status==null?"（未取得HTTP响应）":" HTTP_"+status));}
        finally{db.update("INSERT INTO http_request_observation(source_id,endpoint,attempted_at,completed_at,status,duration_ms,response_bytes,error,failure_kind) VALUES('FRED',?,?,now(),?,?,?,?,?)",visible,java.sql.Timestamp.from(started),status,Duration.between(started,Instant.now()).toMillis(),bytes==null?null:bytes.length,error,error);}
    }
}
