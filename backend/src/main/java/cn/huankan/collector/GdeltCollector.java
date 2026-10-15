package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GdeltCollector {
    private final JdbcTemplate db;private final GdeltDownloader http;private final EvidenceService evidence;private final TransactionTemplate tx;private final CollectorGuard guard;private final GdeltInventory inventory;
    @org.springframework.beans.factory.annotation.Value("${huankan.collector-enabled:false}") private boolean enabled;
    public GdeltCollector(JdbcTemplate db,GdeltDownloader http,EvidenceService evidence,org.springframework.transaction.PlatformTransactionManager manager,CollectorGuard guard,GdeltInventory inventory){this.db=db;this.http=http;this.evidence=evidence;this.guard=guard;this.inventory=inventory;tx=new TransactionTemplate(manager);}
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000,initialDelay=30000)
    public void continueCheckpoint(){if(enabled&&Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gdelt_batch WHERE status<>'SUCCESS' AND availability<>'PERMANENT_FAILURE' AND (retry_at IS NULL OR retry_at<=now())) AND EXISTS(SELECT 1 FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='GDELT' AND s.enabled AND l.status='APPROVED' AND (l.valid_until IS NULL OR l.valid_until>now()))",Boolean.class)))collect();}
    public void collect(){try(var lease=guard.acquire("GDELT")){if(lease!=null)run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("GDELT任务锁异常",e);}}
    private void run(){
        Boolean allowed=db.queryForObject("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='GDELT'",Boolean.class);
        if(!Boolean.TRUE.equals(allowed))throw new IllegalStateException("GDELT未启用或许可未确认");
        UUID job=UUID.randomUUID();long start=System.nanoTime();db.update("INSERT INTO ingestion_job(id,source_id,status,heartbeat_at) VALUES(?,'GDELT','RUNNING',now())",job);
        String batch=null;int[] counts={0,0,0};
        try{
            inventory.discover();
            var queued=db.queryForList("SELECT * FROM gdelt_batch WHERE status<>'SUCCESS' AND availability<>'PERMANENT_FAILURE' AND (retry_at IS NULL OR retry_at<=now()) ORDER BY batch_id LIMIT 3");
            for(var data:queued){
                batch=data.get("batch_id").toString();
                var entry=new GdeltIndex.Entry(batch,"gkg",((Number)data.get("expected_bytes")).longValue(),data.get("expected_md5").toString(),data.get("file_url").toString());
                if(!entry.downloadable(Instant.now()))continue;
                db.update("UPDATE gdelt_batch SET attempts=attempts+1 WHERE batch_id=?",batch);
                byte[] zip=(byte[])data.get("raw_zip");
                if(zip==null){zip=http.get(URI.create(entry.url()),entry.bytes(),batch);String md5=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(zip));if(!md5.equals(entry.md5())){http.removeCompleted(batch);throw new IllegalArgumentException("GDELT清单MD5与真实文件不符");}
                    db.update("UPDATE gdelt_batch SET raw_zip=?,sha256=?,status='PROCESSING',availability='AVAILABLE',error=NULL WHERE batch_id=?",zip,EvidenceService.hash(zip),batch);http.removeCompleted(batch);
                }
                var parsed=GdeltParser.parse(zip,batch);int from=((Number)data.get("completed_lines")).intValue();final String batchId=batch;
                var remaining=parsed.leads().stream().filter(lead->lead.line()>from).toList();
                int[] batchCounts={0,0,0};
                for(int offset=0;offset<remaining.size();offset+=50){
                    var chunk=remaining.subList(offset,Math.min(offset+50,remaining.size()));
                    int[] committed=tx.execute(state->{int[] result={0,0,0};
                    for(var lead:chunk){
                        String title="GDELT聚合线索 · "+lead.publisher()+" · "+lead.themes().substring(0,Math.min(lead.themes().length(),150));
                        String document="url:"+EvidenceService.hash(lead.documentUrl().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        int n=evidence.persist("GDELT",job,lead.raw(),List.of(new SourceAdapter.Item(document,title,entry.url(),null,"聚合金融线索")),Instant.now());
                        if(n>0){var row=db.queryForMap("SELECT id,evidence_id FROM financial_event WHERE source_id='GDELT' AND document_id=?",document);
                            db.update("INSERT INTO aggregated_dependency(evidence_id,batch_id,underlying_url,themes,organizations,countries,batch_processed_at) VALUES(?,?,?,?,?,?,?)",row.get("evidence_id"),batchId,lead.documentUrl(),lead.themes(),lead.organizations(),lead.countries(),java.sql.Timestamp.from(entry.time()));
                            db.update("INSERT INTO event_candidate_link(aggregate_event_id,official_event_id,basis) SELECT ?,e.id,'EXACT_UNDERLYING_URL_MATCH; NOT INDEPENDENT VERIFICATION' FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id JOIN source_registry s ON s.id=e.source_id WHERE s.level='L0' AND r.verification_status='VERIFIED' AND r.source_url=? ON CONFLICT DO NOTHING",row.get("id"),lead.documentUrl());
                        }
                        result[0]+=n;result[1]+=1-n;result[2]++;
                    }
                    db.update("UPDATE gdelt_batch SET status='PROCESSING',completed_lines=?,retained_count=retained_count+? WHERE batch_id=?",chunk.getLast().line(),result[0],batchId);
                    db.update("UPDATE ingestion_job SET fetched_count=fetched_count+?,new_count=new_count+?,duplicate_count=duplicate_count+? WHERE id=?",result[2],result[0],result[1],job);
                    return result;
                    });
                    for(int i=0;i<3;i++){counts[i]+=committed[i];batchCounts[i]+=committed[i];}
                }
                tx.executeWithoutResult(state->{
                    UUID owner=db.queryForObject("SELECT lease_owner FROM ingestion_job WHERE id=?",UUID.class,job);
                    var owners=db.queryForList("SELECT owner FROM collector_lease WHERE source_id='GDELT' AND expires_at>clock_timestamp() FOR SHARE");
                    if(owner==null||owners.isEmpty()||!owner.equals(owners.getFirst().get("owner")))throw new IllegalStateException("采集租约已失效，拒绝完成批次");
                    db.update("UPDATE gdelt_batch SET status='SUCCESS',availability='AVAILABLE',total_lines=?,excluded_count=?,completed_lines=?,finished_at=now(),error=NULL WHERE batch_id=?",parsed.total(),parsed.excluded(),parsed.total(),batchId);
                    db.update("INSERT INTO source_cursor(source_id,last_successful_batch) VALUES('GDELT',?) ON CONFLICT(source_id) DO UPDATE SET last_successful_batch=greatest(source_cursor.last_successful_batch,excluded.last_successful_batch)",batchId);
                    db.update("UPDATE gdelt_gap SET state='RESOLVED',last_checked_at=now() WHERE batch_id=?",batchId);db.update("INSERT INTO collector_batch_commit(job_id,batch_id,fetched,inserted,duplicates) VALUES(?,?,?,?,?)",job,batchId,batchCounts[2],batchCounts[0],batchCounts[1]);
                });
            }
            db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now(),fetched_count=?,new_count=?,duplicate_count=?,duration_ms=? WHERE id=?",counts[2],counts[0],counts[1],(System.nanoTime()-start)/1000000,job);
        }catch(Exception error){String kind=GdeltIndex.failure(error,batch,Instant.now()),detail=error.getClass().getSimpleName()+": "+error.getMessage();
            cn.huankan.reliability.JdbcDiagnostics.failure("GDELT采集/"+job,"TASK_FAILED/"+kind,db,error);
            db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),new_count=?,fetched_count=?,duplicate_count=?,error_code=?,error_detail=?,duration_ms=? WHERE id=?",counts[0],counts[2],counts[1],kind,detail,(System.nanoTime()-start)/1000000,job);
            if(batch!=null)db.update("UPDATE gdelt_batch SET status='FAILED',availability=CASE WHEN attempts>=8 THEN 'PERMANENT_FAILURE' ELSE ? END,error=CASE WHEN attempts>=8 THEN 'AUTO_RETRY_BUDGET_EXHAUSTED: ' ELSE '' END||?,retry_at=now()+least(3600,power(2,least(attempts,10))*60)*interval '1 second' WHERE batch_id=?",kind,detail,batch);
        }
    }
}
