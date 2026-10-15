package cn.huankan.event;

import cn.huankan.reliability.IsolatedReliabilityDatabase;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BoundedEventQueryTest extends IsolatedReliabilityDatabase {
    @Test void deliveryTimingEnrichmentIsBoundedByReturnedPage() throws Exception {
        db.execute("CREATE TEMP TABLE r1_fixture AS SELECT gen_random_uuid() evidence_id,gen_random_uuid() event_id,n FROM generate_series(1,5000)n");
        UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FED','SUCCESS')",job);
        db.update("INSERT INTO raw_evidence(id,source_id,source_document_id,source_url,published_at,first_seen_at,raw_content,raw_content_hash,item_fingerprint,content_language,parser_version,revision,verification_status,job_id,ingestion_kind) SELECT evidence_id,'FED','r1-isolated-'||n,'https://www.federalreserve.gov/fixture/'||n,now()-n*interval '1 second',now(),convert_to('ISOLATED R1','UTF8'),encode(sha256(convert_to('ISOLATED R1','UTF8')),'hex'),repeat('1',64),'zh-CN','r1-test',1,'PENDING',?,'HISTORICAL_BACKFILL' FROM r1_fixture",job);
        db.update("INSERT INTO financial_event(id,source_id,document_id,original_title,category,evidence_id,ingestion_kind) SELECT event_id,'FED','r1-isolated-'||n,'【隔离测试】分页 '||n,'测试',evidence_id,'HISTORICAL_BACKFILL' FROM r1_fixture");
        db.update("INSERT INTO stream_outbox(event_id,evidence_id,notification_kind) SELECT event_id,evidence_id,'HISTORICAL_BACKFILL' FROM r1_fixture");
        db.execute("ANALYZE financial_event");db.execute("ANALYZE raw_evidence");db.execute("ANALYZE stream_outbox");
        var captured=new ArrayList<String>();var parameters=new ArrayList<Object[]>();
        JdbcTemplate spy=spy(db);
        doAnswer(invocation->{String query=invocation.getArgument(0);captured.add(query);Object[] args=Arrays.copyOfRange(invocation.getArguments(),1,invocation.getArguments().length);parameters.add(args);return invocation.callRealMethod();}).when(spy).queryForList(anyString(),any(Object[].class));
        var result=new QueryService(spy).events("","FED",1,"","","published");
        assertEquals(5000L,result.get("total"));assertEquals(50,((List<?>)result.get("items")).size());
        String sql=captured.getFirst();Object[] args=parameters.getFirst();
        String plan=tx.execute(status->{db.execute("SET LOCAL statement_timeout='10s'");return db.queryForObject("EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) "+sql,String.class,args);});
        JsonNode tree=new ObjectMapper().readTree(plan);List<Integer> loops=new ArrayList<>();collect(tree,loops);
        record("事件列表真实SQL查询计划",Map.of("rows",5000,"page_size",50,"sql",sql,"delivery_aggregate_loops",loops,"plan",tree));
        assertFalse(loops.isEmpty());assertTrue(loops.stream().allMatch(n->n<=50),"投递时间聚合不应先遍历全体匹配记录，再分页");
    }
    private static void collect(JsonNode node,List<Integer> loops){if(node.isObject()){if("Aggregate".equals(node.path("Node Type").asText())&&node.path("Actual Loops").asInt()>1)loops.add(node.path("Actual Loops").asInt());node.elements().forEachRemaining(n->collect(n,loops));}else if(node.isArray())node.elements().forEachRemaining(n->collect(n,loops));}
}
