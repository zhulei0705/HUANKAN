package cn.huankan.reliability;

import cn.huankan.collector.*;
import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EcbChunkLifecycleTest extends IsolatedReliabilityDatabase {
    @Test void committedObservationsAndResponseProvenanceSurviveChunkFailureAndIdempotentRetry() throws Exception {
        normalPool(4);
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='S69-R3 ISOLATED ONLY' WHERE source_id='ECB_DATA'");
        db.update("UPDATE source_registry SET enabled=true WHERE id='ECB_DATA'");
        var http=mock(OfficialHttpClient.class);
        when(http.get(any(URI.class),anyString(),anyMap())).thenAnswer(inv->{URI uri=inv.getArgument(0);String path=uri.getPath();
            String flow=path.contains("/EXR/")?"EXR":"FM";
            byte[] body=path.contains("/dataflow/")?structure(flow):csv(flow,path.substring(path.lastIndexOf('/')+1));
            return new OfficialHttpClient.Result(200,body,null,null);
        });
        var evidence=spy(new EvidenceService(db));var calls=new AtomicInteger();
        doAnswer(inv->{if(calls.incrementAndGet()==56)throw new IllegalStateException("【隔离故障注入】ECB第二分批失败");return inv.callRealMethod();}).when(evidence).persist(anyString(),any(),any(byte[].class),anyList(),any());
        var collector=new EcbDataCollector(db,http,evidence,new CollectorGuard(db),new DataSourceTransactionManager(pool));
        collector.collect();
        assertEquals(50,count("raw_evidence"));assertEquals(50,count("macro_observation"));assertEquals(50,count("response_evidence"));assertEquals(50,count("stream_outbox"));
        var failure=db.queryForMap("SELECT status,new_count,fetched_count FROM ingestion_job WHERE source_id='ECB_DATA'");assertEquals("FAILED",failure.get("status"));assertEquals(50,failure.get("new_count"));
        collector.collect();
        assertEquals(420,count("raw_evidence"));assertEquals(420,count("macro_observation"));assertEquals(420,count("stream_outbox"));
        var success=db.queryForMap("SELECT new_count,fetched_count,duplicate_count FROM ingestion_job WHERE status='SUCCESS'");
        assertEquals(370,success.get("new_count"));assertEquals(420,success.get("fetched_count"));assertEquals(50,success.get("duplicate_count"));
        assertEquals(1,count("ingestion_quarantine"));assertEquals(0,count("collector_lease"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM raw_evidence r WHERE NOT EXISTS(SELECT 1 FROM response_evidence x JOIN official_response o ON o.id=x.response_id WHERE x.evidence_id=r.id AND o.sha256=encode(sha256(o.raw_content),'hex'))",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM collection_task_accounting WHERE counter_state='COUNTER_MISMATCH'",Integer.class));
        record("S69-R3/ECB分批失败和幂等恢复",Map.of("expected_fault",failure,"retry",success,"observations",420,"outbox",420,"failed_quarantine_retained",1,"evidence_provenance_errors",0));
    }
    private byte[] csv(String flow,String key){
        String header="EXR".equals(flow)?"KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS,UNIT,UNIT_MULT,TITLE\n":"KEY,FREQ,REF_AREA,CURRENCY,PROVIDER_FM,INSTRUMENT_FM,PROVIDER_FM_ID,DATA_TYPE_FM,TIME_PERIOD,OBS_VALUE,OBS_STATUS,UNIT,UNIT_MULT,TITLE\n";
        var result=new StringBuilder(header);String[] parts=key.split("\\.");
        for(int n=0;n<60;n++){String date=LocalDate.now().minusDays(70-n).toString();
            if("EXR".equals(flow))result.append(flow+"."+key+",D,"+parts[1]+",EUR,SP00,A,"+date+",1.25,A,"+parts[1]+",0,隔离参考汇率\n");
            else result.append(flow+"."+key+",B,U2,EUR,4F,KR,"+parts[5]+",LEV,"+date+",2.5,A,PCPA,0,隔离政策利率\n");
        }return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private byte[] structure(String flow){
        var text=new StringBuilder("<Structure xmlns:str='http://www.sdmx.org/resources/sdmxml/schemas/v2_1/structure'><str:Dataflow id='"+flow+"' agencyID='ECB'/><str:DataStructure id='"+(flow.equals("EXR")?"ECB_EXR1":"ECB_FMD2")+"'><str:DimensionList>");
        var dimensions=flow.equals("EXR")?List.of("FREQ","CURRENCY","CURRENCY_DENOM","EXR_TYPE","EXR_SUFFIX"):List.of("FREQ","REF_AREA","CURRENCY","PROVIDER_FM","INSTRUMENT_FM","PROVIDER_FM_ID","DATA_TYPE_FM");
        int n=0;for(String dimension:dimensions)text.append("<str:Dimension id='"+dimension+"' position='"+(++n)+"'/>");text.append("</str:DimensionList></str:DataStructure>");
        for(var entry:Map.of("CL_PROVIDER_FM_ID",List.of("DFR","MRR_FR","MLFR"),"CL_PROVIDER_FM",List.of("4F"),"CL_INSTRUMENT_FM",List.of("KR"),"CL_FREQ",List.of("B"),"CL_UNIT",List.of("PCPA")).entrySet()){
            text.append("<str:Codelist id='"+entry.getKey()+"'>");for(String code:entry.getValue())text.append("<str:Code id='"+code+"'/>");text.append("</str:Codelist>");
        }return text.append("</Structure>").toString().getBytes(StandardCharsets.UTF_8);
    }
}
