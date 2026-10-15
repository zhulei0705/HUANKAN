package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NbsCollector {
    private final JdbcTemplate db; private final OfficialHttpClient http; private final EvidenceService evidence; private final CollectorGuard guard; private final TransactionTemplate tx;
    public NbsCollector(JdbcTemplate db,OfficialHttpClient http,EvidenceService evidence,CollectorGuard guard,PlatformTransactionManager manager){this.db=db;this.http=http;this.evidence=evidence;this.guard=guard;this.tx=new TransactionTemplate(manager);}
    public record Segment(SourceAdapter.Item item,byte[] bytes){}
    public static List<Segment> segments(byte[] body){
        // Preserve exact UTF-8 response slices, rather than serialising DOM nodes.
        String xml=new String(body,StandardCharsets.UTF_8);
        if(!Arrays.equals(body,xml.getBytes(StandardCharsets.UTF_8))||!xml.matches("(?s).*encoding=[\"']UTF-8[\"'].*"))throw new IllegalArgumentException("RSS编码未经确认");
        var parsed=new RssAdapter("www.stats.gov.cn").parse(body);
        var matches=java.util.regex.Pattern.compile("<item(?:\\s[^>]*)?>.*?</item>",java.util.regex.Pattern.DOTALL).matcher(xml);
        var result=new ArrayList<Segment>();int i=0;
        while(matches.find()){if(i>=parsed.size())throw new IllegalArgumentException("RSS条目边界不一致");result.add(new Segment(parsed.get(i++),matches.group().getBytes(StandardCharsets.UTF_8)));}
        if(i!=parsed.size()||result.isEmpty())throw new IllegalArgumentException("RSS条目缺失或边界不一致");return List.copyOf(result);
    }
    public void collect(){
        Boolean allowed=db.queryForObject("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='NBS'",Boolean.class);
        if(!Boolean.TRUE.equals(allowed))throw new IllegalStateException("国家统计局许可待核验或未启用");
        try(var lease=guard.acquire("NBS")){if(lease==null)return;
            UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'NBS','RUNNING')",job);long start=System.nanoTime();int inserted=0;Integer status=null;
            boolean initialImport=db.queryForObject("SELECT count(*) FROM raw_evidence WHERE source_id='NBS'",Long.class)==0;
            try{
                for(String section:List.of("zxfb","sjjd")){
                    URI uri=URI.create("https://www.stats.gov.cn/sj/"+section+"/rss.xml");var response=http.get(uri,"HUANKAN/0.1 (local official RSS reader)",Map.of());status=response.status();if(status!=200)throw new IllegalStateException("HTTP_"+status);
                    var items=segments(response.body());Instant acquired=Instant.now();UUID responseId=UUID.randomUUID();
                    int count=tx.execute(s->{db.update("INSERT INTO official_response(id,source_id,job_id,url,response_status,raw_content,sha256,acquired_at) VALUES(?,'NBS',?,?,200,?,?,?)",responseId,job,uri.toString(),response.body(),EvidenceService.hash(response.body()),java.sql.Timestamp.from(acquired));int added=0;
                        // All first-import items use the same monitoring boundary, including feeds with missing times.
                        db.update("INSERT INTO source_cursor(source_id,monitoring_started_at) VALUES('NBS',?) ON CONFLICT(source_id) DO NOTHING",java.sql.Timestamp.from(acquired));
                        for(var item:items){added+=evidence.persist("NBS",job,item.bytes(),List.of(item.item()),acquired,initialImport);UUID id=db.queryForObject("SELECT id FROM raw_evidence WHERE source_id='NBS' AND source_document_id=? ORDER BY revision DESC LIMIT 1",UUID.class,item.item().documentId());db.update("INSERT INTO response_evidence(response_id,evidence_id,section) VALUES(?,?,?) ON CONFLICT DO NOTHING",responseId,id,section);}
                        db.update("UPDATE ingestion_job SET fetched_count=fetched_count+?,duplicate_count=duplicate_count+? WHERE id=?",items.size(),items.size()-added,job);return added;});inserted+=count;
                }
                db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now(),http_status=?,new_count=?,duration_ms=? WHERE id=?",status,inserted,(System.nanoTime()-start)/1000000,job);
            }catch(Exception e){db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),http_status=?,new_count=?,error_code=?,error_detail=?,quarantine_count=1,duration_ms=? WHERE id=?",status,inserted,CollectorGuard.failure(e),Objects.toString(e.getMessage(),e.getClass().getSimpleName()).substring(0,Math.min(500,Objects.toString(e.getMessage(),e.getClass().getSimpleName()).length())),(System.nanoTime()-start)/1000000,job);db.update("INSERT INTO ingestion_quarantine(id,job_id,reason) VALUES(?,?,?)",UUID.randomUUID(),job,CollectorGuard.failure(e));}
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("国家统计局采集锁异常",e);}
    }
}
