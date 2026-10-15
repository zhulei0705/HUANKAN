package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EcbDataCollector {
    private final JdbcTemplate db;private final OfficialHttpClient http;private final EvidenceService evidence;private final CollectorGuard guard;private final TransactionTemplate tx;private final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
    public EcbDataCollector(JdbcTemplate db,OfficialHttpClient http,EvidenceService evidence,CollectorGuard guard,PlatformTransactionManager manager){this.db=db;this.http=http;this.evidence=evidence;this.guard=guard;tx=new TransactionTemplate(manager);}
    private UUID response(UUID job,URI uri,byte[] bytes){UUID id=UUID.randomUUID();db.update("INSERT INTO official_response(id,source_id,job_id,url,response_status,raw_content,sha256) VALUES(?,'ECB_DATA',?,?,200,?,?)",id,job,uri.toString(),bytes,EvidenceService.hash(bytes));return id;}
    public void collect(){
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT s.enabled AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) FROM source_registry s JOIN source_license l ON l.source_id=s.id WHERE s.id='ECB_DATA'",Boolean.class)))throw new IllegalStateException("ECB经济数据许可待核验或未启用");
        try(var lease=guard.acquire("ECB_DATA")){if(lease==null)return;UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'ECB_DATA','RUNNING')",job);long start=System.nanoTime();Integer status=null;int total=0;
            try{
                URI flow=URI.create("https://data-api.ecb.europa.eu/service/dataflow/ECB/EXR/latest?references=all");var structure=http.get(flow,"HUANKAN/0.1 (local official statistics reader)",Map.of());status=structure.status();if(status!=200)throw new IllegalStateException("HTTP_"+status);SdmxCsv.validateStructure(structure.body());tx.executeWithoutResult(s->response(job,flow,structure.body()));
                URI policyFlow=URI.create("https://data-api.ecb.europa.eu/service/dataflow/ECB/FM/latest?references=all");var policyStructure=http.get(policyFlow,"HUANKAN/0.1 (local official statistics reader)",Map.of());status=policyStructure.status();if(status!=200)throw new IllegalStateException("HTTP_"+status);SdmxCsv.validatePolicyStructure(policyStructure.body());tx.executeWithoutResult(s->response(job,policyFlow,policyStructure.body()));
                for(String series:List.of("EXR.D.USD.EUR.SP00.A","EXR.D.JPY.EUR.SP00.A","EXR.D.GBP.EUR.SP00.A","EXR.D.CNY.EUR.SP00.A","FM.B.U2.EUR.4F.KR.DFR.LEV","FM.B.U2.EUR.4F.KR.MRR_FR.LEV","FM.B.U2.EUR.4F.KR.MLFR.LEV")){
                    String dataflow=series.substring(0,series.indexOf('.')),key=series.substring(series.indexOf('.')+1);URI uri=URI.create("https://data-api.ecb.europa.eu/service/data/"+dataflow+"/"+key+"?startPeriod="+LocalDate.now(ZoneOffset.UTC).minusDays(90)+"&format=csvdata");
                    var result=http.get(uri,"HUANKAN/0.1 (local official statistics reader)",Map.of("Accept","text/csv"));status=result.status();if(status!=200)throw new IllegalStateException("HTTP_"+status);var observations=SdmxCsv.parse(result.body(),series);if(observations.isEmpty())throw new IllegalArgumentException("SDMX没有实际观测值");Instant acquired=Instant.now();
                    UUID[] receipt={null};
                    for(int offset=0;offset<observations.size();offset+=50){
                    var chunk=observations.subList(offset,Math.min(offset+50,observations.size()));
                    int added=tx.execute(s->{UUID responseId=receipt[0]==null?response(job,uri,result.body()):receipt[0];var metadata=observations.getFirst().metadata();String encoded;try{encoded=json.writeValueAsString(metadata);}catch(Exception e){throw new IllegalArgumentException(e);}
                        db.update("INSERT INTO macro_series(id,source_id,flow,series_key,title,units,frequency,currency,currency_denominator,metadata,response_id) VALUES(?,'ECB_DATA',?,?,?,?,?,?,?,?::jsonb,?) ON CONFLICT(id) DO UPDATE SET title=excluded.title,units=excluded.units,metadata=excluded.metadata,response_id=excluded.response_id,updated_at=now()",series,dataflow,key,metadata.get("TITLE"),metadata.get("UNIT"),metadata.get("FREQ"),metadata.get("CURRENCY"),metadata.get("CURRENCY_DENOM"),encoded,responseId);int count=0;
                        for(var obs:chunk){String title=metadata.get("TITLE")+" · "+obs.date()+" · "+(obs.value()==null?"缺失值":obs.value().toPlainString())+" · "+obs.status();String document=series+":"+obs.date();var item=new SourceAdapter.Item(document,title,"https://data-api.ecb.europa.eu/service/data/"+dataflow+"/"+key,null,dataflow.equals("EXR")?"官方参考汇率":"官方政策利率");int inserted=evidence.persist("ECB_DATA",job,result.body(),List.of(item),acquired);count+=inserted;UUID id=db.queryForObject("SELECT id FROM raw_evidence WHERE source_id='ECB_DATA' AND source_document_id=? ORDER BY revision DESC LIMIT 1",UUID.class,document);
                            db.update("INSERT INTO macro_observation(evidence_id,series_id,observation_date,value,observation_status,version) SELECT ?,?,?,?,?,revision FROM raw_evidence WHERE id=? ON CONFLICT(evidence_id) DO NOTHING",id,series,java.sql.Date.valueOf(obs.date()),obs.value(),obs.status(),id);db.update("INSERT INTO response_evidence(response_id,evidence_id) VALUES(?,?) ON CONFLICT DO NOTHING",responseId,id);}
                        db.update("UPDATE ingestion_job SET fetched_count=fetched_count+?,new_count=new_count+?,duplicate_count=duplicate_count+? WHERE id=?",chunk.size(),count,chunk.size()-count,job);return count;});total+=added;
                    // Retrieve only a committed receipt; a failed chunk cannot publish its UUID.
                    if(receipt[0]==null)receipt[0]=db.queryForObject("SELECT response_id FROM macro_series WHERE id=?",UUID.class,series);
                    }
                }
                db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now(),http_status=?,new_count=?,duration_ms=? WHERE id=?",status,total,(System.nanoTime()-start)/1000000,job);
            }catch(Exception e){cn.huankan.reliability.JdbcDiagnostics.failure("ECB数据采集/"+job,"TASK_FAILED/"+CollectorGuard.failure(e),db,e);String message=Objects.toString(e.getMessage(),e.getClass().getSimpleName());db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),http_status=?,new_count=?,error_code=?,error_detail=?,duration_ms=?,quarantine_count=1 WHERE id=?",status,total,CollectorGuard.failure(e),message.substring(0,Math.min(500,message.length())),(System.nanoTime()-start)/1000000,job);db.update("INSERT INTO ingestion_quarantine(id,job_id,reason) VALUES(?,?,?)",UUID.randomUUID(),job,CollectorGuard.failure(e));}
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("ECB经济数据采集锁异常",e);}
    }
}
