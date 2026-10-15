package cn.huankan.reliability;

import cn.huankan.stream.StreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class ObservabilityDatabaseTest extends IsolatedReliabilityDatabase {
    private final SimpleMeterRegistry metrics=new SimpleMeterRegistry();
    private ObservedDataSource measured(){var observed=new ObservedDataSource(pool,()->metrics);db.setDataSource(observed);tx=new TransactionTemplate(new DataSourceTransactionManager(observed));return observed;}
    private void facts(String scene,Object data){record("S69-R2/"+scene,Map.of("database","huankan_test","port",55432,"facts",data));}

    @Test void realCollectorLeaseAndBatchTransactionCanCompeteWithSse() throws Exception {
        var observed=measured();var guard=new cn.huankan.collector.CollectorGuard(db);var stream=new StreamService(db,new ObjectMapper());
        try(var leaseContext=new DiagnosticContext("隔离GDELT实际互斥锁");var lease=guard.acquire("GDELT")){
            assertNotNull(lease);
            try(var batchContext=new DiagnosticContext("隔离GDELT批次事务");var batch=observed.getConnection()){
                batch.setAutoCommit(false);batch.createStatement().execute("SELECT pg_sleep(0.1)");
                assertEquals(2,observed.holders().size());var failure=assertThrows(Exception.class,stream::publish);
                facts("真实采集租约加批次事务与SSE竞争",Map.of("holders",observed.holders(),"failure",JdbcDiagnostics.evidence("隔离SSE","FAILED",db,failure),"maximum",2,"limitation","仅复现机制，不能推定正式10连接历史持有者"));batch.rollback();
            }
            assertDoesNotThrow(stream::publish);
        }
        assertTrue(observed.holders().isEmpty());assertEquals(0,count("collector_lease"));assertEquals(0,count("raw_evidence"));assertEquals(0,count("stream_outbox"));
    }

    @Test void measuredSlowSqlAndTransactionKeepContextAndReleaseConnection() throws Exception {
        var observed=measured();String correlation;
        try(var context=new DiagnosticContext("隔离慢查询与长事务")){
            correlation=DiagnosticContext.id();
            tx.executeWithoutResult(s->{db.execute("SELECT pg_sleep(1.1)");assertEquals(correlation,observed.holders().getFirst().get("diagnostic_id"));assertEquals(1,observed.holders().size());
                assertEquals(1,new JdbcTemplate(base).queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname='huankan_test' AND application_name=? AND xact_start<clock_timestamp()-interval '1 second'",Integer.class,schema));});
        }
        assertEquals("无上游关联",DiagnosticContext.id());assertTrue(observed.holders().isEmpty());
        assertEquals(1,metrics.get("huankan.jdbc.transaction").tag("outcome","commit").timer().count());
        assertTrue(metrics.get("huankan.jdbc.sql").tag("outcome","success").timer().totalTime(TimeUnit.MILLISECONDS)>=1000);
        facts("真实pg_sleep与事务计时",Map.of("sql_ms",metrics.get("huankan.jdbc.sql").tag("outcome","success").timer().totalTime(TimeUnit.MILLISECONDS),"transaction_ms",metrics.get("huankan.jdbc.transaction").tag("outcome","commit").timer().totalTime(TimeUnit.MILLISECONDS),"holders_after",observed.holders()));
    }

    @Test void timedOutOrdinaryRequestIdentifiesHeldConnectionsAndRecovers() throws Exception {
        var observed=measured();
        try(var context=new DiagnosticContext("隔离批量采集持有者");var first=observed.getConnection();var second=observed.getConnection()){
            assertEquals(2,observed.holders().size());
            var failure=assertThrows(Exception.class,()->db.queryForObject("SELECT 1",Integer.class));
            assertEquals(1,metrics.get("huankan.jdbc.acquire.timeouts").counter().count());
            facts("池饱和与普通查询竞争",Map.of("holders",observed.holders(),"failure",JdbcDiagnostics.evidence("隔离普通查询","FAILED",db,failure),"acquire_error_ms",metrics.get("huankan.jdbc.acquire").tag("outcome","error").timer().totalTime(TimeUnit.MILLISECONDS)));
        }
        assertEquals(1,db.queryForObject("SELECT 1",Integer.class));assertTrue(observed.holders().isEmpty());facts("释放后请求恢复",Map.of("result",1,"active",pool.getHikariPoolMXBean().getActiveConnections()));
    }

    @Test void actualLockWaitAndLongTransactionCanBeSeenWithoutQueryText() throws Exception {
        var observed=measured();db.execute("CREATE TABLE r2_lock(id int primary key,value int)");db.update("INSERT INTO r2_lock VALUES(1,0)");
        var executor=Executors.newSingleThreadExecutor();
        try(var first=observed.getConnection()){
            first.setAutoCommit(false);first.createStatement().execute("UPDATE r2_lock SET value=1 WHERE id=1");
            Future<Throwable> waiting=executor.submit(()->{try(var context=new DiagnosticContext("隔离锁等待");var second=observed.getConnection()){second.setAutoCommit(false);second.createStatement().execute("SET LOCAL lock_timeout='500ms'");second.createStatement().execute("UPDATE r2_lock SET value=2 WHERE id=1");return null;}catch(Throwable failure){return failure;}});
            List<Map<String,Object>> snapshot=List.of();long start=System.nanoTime();
            var diagnostic=new JdbcTemplate(base);
            while(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(400)){
                snapshot=diagnostic.queryForList("SELECT pid,state,wait_event_type,wait_event,xact_start,query_start FROM pg_stat_activity WHERE datname='huankan_test' AND application_name=? AND wait_event_type='Lock'",schema);
                if(!snapshot.isEmpty())break;Thread.sleep(10);
            }
            assertFalse(snapshot.isEmpty());var failure=waiting.get(2,TimeUnit.SECONDS);assertInstanceOf(SQLException.class,failure);assertEquals("55P03",((SQLException)failure).getSQLState());
            facts("真实锁等待与长事务",Map.of("snapshot",snapshot,"holders",observed.holders(),"sql_state",((SQLException)failure).getSQLState()));first.rollback();
        }finally{executor.shutdownNow();}
        db.update("UPDATE r2_lock SET value=3 WHERE id=1");assertEquals(3,db.queryForObject("SELECT value FROM r2_lock",Integer.class));assertTrue(observed.holders().isEmpty());
    }

    @Test void schedulerAndSseShareCapacityAndRecoverWithMeasuredOutcomes() throws Exception {
        var observed=measured();var target=new StreamService(db,new ObjectMapper());var factory=new AspectJProxyFactory(target);factory.addAspect(new ExecutionDiagnostics(metrics));StreamService stream=factory.getProxy();
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setErrorHandler(e->facts("调度失败证据",JdbcDiagnostics.evidence("StreamService.publish","FAILED",db,e)));scheduler.initialize();var successes=new AtomicInteger();ScheduledFuture<?> task=null;
        try{
            try(var context=new DiagnosticContext("隔离后台批量持有");var first=observed.getConnection();var second=observed.getConnection()){
                task=scheduler.scheduleWithFixedDelay(()->{stream.publish();successes.incrementAndGet();},Duration.ofMillis(100));
                long start=System.nanoTime();while(metrics.find("huankan.scheduler.duration").tag("outcome","error").timer()==null&&System.nanoTime()-start<TimeUnit.SECONDS.toNanos(3))Thread.sleep(10);
                assertNotNull(metrics.find("huankan.scheduler.duration").tag("outcome","error").timer());assertEquals(2,observed.holders().size());
            }
            long start=System.nanoTime();while(successes.get()==0&&System.nanoTime()-start<TimeUnit.SECONDS.toNanos(3))Thread.sleep(10);assertTrue(successes.get()>0);
            facts("SSE调度容量竞争与恢复",Map.of("failures",metrics.get("huankan.scheduler.duration").tag("outcome","error").timer().count(),"successes",successes.get(),"maximum",pool.getMaximumPoolSize()));
        }finally{if(task!=null)task.cancel(false);scheduler.shutdown();}
    }

    @Test void sqlFailureRecordsRealStateAndRollbackWithoutLeakingParameters(){
        measured();var failure=assertThrows(Exception.class,()->tx.executeWithoutResult(s->{db.execute("SET LOCAL statement_timeout='80ms'");db.execute("SELECT pg_sleep(2)");}));
        assertEquals(1,metrics.get("huankan.jdbc.sql").tag("outcome","error").timer().count());assertEquals(1,metrics.get("huankan.jdbc.transaction").tag("outcome","rollback").timer().count());
        assertEquals(1,db.queryForObject("SELECT 1",Integer.class));facts("SQL超时与回滚计数",JdbcDiagnostics.evidence("隔离SQL","FAILED",db,failure));
    }
}
