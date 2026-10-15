package cn.huankan.event;

import cn.huankan.reliability.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.interceptor.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

class ResearchSchedulerLifecycleTest extends IsolatedReliabilityDatabase {
    @SuppressWarnings("unchecked") private <T> T transactional(T target){
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(pool),new MatchAlwaysTransactionAttributeSource()));return (T)factory.getProxy();
    }
    @Test void actualSchedulerRetainsFiveHundredRowBudgetAndCommitsEachFiftyRows() throws Exception {
        normalPool(4);
        db.execute("CREATE TEMP TABLE r3_seed AS SELECT gen_random_uuid() evidence_id,gen_random_uuid() event_id,n FROM generate_series(1,500)n");
        UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FED','SUCCESS')",job);
        db.update("INSERT INTO raw_evidence(id,source_id,source_document_id,source_url,published_at,first_seen_at,raw_content,raw_content_hash,item_fingerprint,content_language,parser_version,revision,verification_status,job_id,ingestion_kind) SELECT evidence_id,'FED','r3-scheduler-'||n,'https://www.federalreserve.gov/fixture/'||n,now(),now(),convert_to('Federal Reserve ISOLATED','UTF8'),encode(sha256(convert_to('Federal Reserve ISOLATED','UTF8')),'hex'),repeat('1',64),'en','r3-test',1,'PENDING',?,'HISTORICAL_BACKFILL' FROM r3_seed",job);
        db.update("INSERT INTO financial_event(id,source_id,document_id,original_title,category,evidence_id,ingestion_kind) SELECT event_id,'FED','r3-scheduler-'||n,'Federal Reserve interest rate '||n::text,'隔离测试',evidence_id,'HISTORICAL_BACKFILL' FROM r3_seed");
        var research=transactional(new ResearchService(db,false));var governance=transactional(new GovernanceService(db,new ObjectMapper()));
        long before=System.nanoTime();assertEquals(500,((Map<?,?>)governance.process(500)).get("processed"));double originalMs=(System.nanoTime()-before)/1e6;
        db.update("UPDATE governed_message SET rule_version='待重处理'");
        var metrics=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();var observed=new ObservedDataSource(pool,()->metrics);db.setDataSource(observed);
        var factory=new ProxyFactory(new ResearchService(db,false));factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(observed),new MatchAlwaysTransactionAttributeSource()));research=(ResearchService)factory.getProxy();
        factory=new ProxyFactory(new GovernanceService(db,new ObjectMapper()));factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(observed),new MatchAlwaysTransactionAttributeSource()));governance=(GovernanceService)factory.getProxy();
        long start=System.nanoTime();new ResearchScheduler(research,governance,true).run();double elapsed=(System.nanoTime()-start)/1e6;
        assertEquals(500,count("event_candidate"));assertEquals(500,count("governed_message"));assertEquals(500,count("research_case_message"));
        var timer=metrics.get("huankan.jdbc.transaction").tag("outcome","commit").timer();assertEquals(20,timer.count());assertTrue(observed.holders().isEmpty());
        record("S69-R3/实际治理调度分批",Map.of("rows",500,"original_governance_transaction_ms",originalMs,"new_research_and_governance_round_ms",elapsed,"commit_count",timer.count(),"new_transaction_max_ms",timer.max(TimeUnit.MILLISECONDS),"silent_drop",0,"limitation","原始值为仅治理；新值含研究及治理，不作为同工作量吞吐比较"));
        new ResearchScheduler(research,governance,true).run();assertEquals(22,timer.count());assertEquals(500,count("event_candidate"));
    }
}
