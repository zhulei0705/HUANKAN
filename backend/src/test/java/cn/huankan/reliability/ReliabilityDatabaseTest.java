package cn.huankan.reliability;

import cn.huankan.collector.*;
import cn.huankan.evidence.EvidenceService;
import cn.huankan.stream.StreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReliabilityDatabaseTest extends IsolatedReliabilityDatabase {
    private SQLException sqlException(Throwable failure){for(Throwable c=failure;c!=null;c=c.getCause())if(c instanceof SQLException sql)return sql;throw new AssertionError("未得到真实SQLException",failure);}
    private Map<String,Object> failure(Throwable e){return JdbcDiagnostics.evidence("隔离故障注入","FAILED",db,e);}
    private UUID job(){var id=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FED','RUNNING')",id);return id;}
    private SourceAdapter.Item item(){return new SourceAdapter.Item("r1-isolated-idempotent","【隔离测试】幂等证据","https://www.federalreserve.gov/fixture/r1",Instant.parse("2026-10-13T10:00:00Z"),"测试");}
    private int save(UUID job){return tx.execute(s->new EvidenceService(db).persist("FED",job,"R1 ISOLATED".getBytes(java.nio.charset.StandardCharsets.UTF_8),List.of(item()),Instant.now()));}
    private HikariDataSource replacement(){var next=new HikariDataSource();next.setJdbcUrl(base.getUrl()+"?currentSchema="+schema);next.setUsername(System.getenv("HUANKAN_TEST_DB_USER"));next.setPassword(System.getenv("HUANKAN_TEST_DB_PASSWORD"));next.setMaximumPoolSize(2);next.setMinimumIdle(1);next.setConnectionTimeout(300);next.setPoolName(schema+"_recovered");next.addDataSourceProperty("ApplicationName",schema);return next;}

    @Test void closedPoolAcquisitionFailureIsRecorded(){pool.close();var e=assertThrows(Exception.class,()->db.queryForObject("SELECT 1",Integer.class));assertNotNull(sqlException(e));record("JDBC连接获取失败",failure(e));}

    @Test void onlyOwnedTestBackendCanBeInterruptedAndPoolRecovers() throws Exception {
        try(var connection=pool.getConnection()){
            int pid;try(var s=connection.createStatement();var r=s.executeQuery("SELECT pg_backend_pid()")){r.next();pid=r.getInt(1);}
            Boolean stopped=new JdbcTemplate(base).queryForObject("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid=? AND datname='huankan_test' AND usename=current_user AND application_name=?",Boolean.class,pid,schema);assertEquals(true,stopped);
            var e=assertThrows(SQLException.class,()->connection.createStatement().execute("SELECT 1"));assertTrue(Set.of("57P01","08006").contains(e.getSQLState()));record("仅中断本次隔离连接",failure(e));
        }
        assertEquals(1,db.queryForObject("SELECT 1",Integer.class));record("连接中断后池恢复",JdbcDiagnostics.pool(db));
    }

    @Test void fullPoolExposesActualPendingAndTimeoutWithoutIncreasingCapacity() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        try(var first=pool.getConnection();var second=pool.getConnection()){
            Future<Throwable> attempt=executor.submit(()->{try(var ignored=pool.getConnection()){return null;}catch(SQLException e){return e;}});
            long start=System.nanoTime();while(pool.getHikariPoolMXBean().getThreadsAwaitingConnection()==0&&System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(200))Thread.sleep(5);
            assertEquals(1,pool.getHikariPoolMXBean().getThreadsAwaitingConnection());var pending=JdbcDiagnostics.pool(db);Throwable e=attempt.get(2,TimeUnit.SECONDS);assertInstanceOf(SQLTransientConnectionException.class,e);assertEquals("DATABASE_CONNECTION_TIMEOUT",CollectorGuard.failure(e));record("连接池等待超时",Map.of("pending_snapshot",pending,"failure",failure(e)));
        }finally{executor.shutdownNow();}
        assertEquals(1,db.queryForObject("SELECT 1",Integer.class));assertEquals(2,pool.getMaximumPoolSize());
    }

    @Test void statementTimeoutIsSqlFailureAndTransactionConnectionReturns(){
        var e=assertThrows(Exception.class,()->tx.executeWithoutResult(s->{db.execute("SET LOCAL statement_timeout='100ms'");db.execute("SELECT pg_sleep(2)");}));
        assertEquals("57014",sqlException(e).getSQLState());assertEquals("DATABASE_QUERY_TIMEOUT",CollectorGuard.failure(e));assertEquals(1,db.queryForObject("SELECT 1",Integer.class));record("SQL执行超时与事务回滚",failure(e));
    }

    @Test void evidenceAndOutboxRollbackTogetherAndFailureRecordSurvives(){
        UUID job=job();assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->{new EvidenceService(db).persist("FED",job,"R1 ROLLBACK".getBytes(),List.of(item()),Instant.now());throw new IllegalStateException("【隔离测试】事务回滚");}));
        assertEquals(0,count("raw_evidence"));assertEquals(0,count("stream_outbox"));db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),error_code='DATABASE_TRANSACTION_FAILURE' WHERE id=?",job);assertEquals(1,count("ingestion_job"));record("事务与Outbox原子回滚",Map.of("evidence",0,"outbox",0,"failed_jobs",1));
    }

    @Test void failedCollectionRetriesKeepFailureAndDeduplicateRealCommit(){
        var http=mock(OfficialHttpClient.class);String xml="<rss><channel><item><title>【隔离测试】官方格式</title><link>https://www.federalreserve.gov/newsevents/pressreleases/r1-test.htm</link><guid>r1-isolated-retry</guid><pubDate>Tue, 13 Oct 2026 10:00:00 GMT</pubDate></item></channel></rss>";
        var evidence=spy(new EvidenceService(db));doAnswer(i->tx.execute(s->{try{return i.callRealMethod();}catch(Throwable failure){throw new IllegalStateException(failure);}})).when(evidence).persist(anyString(),any(UUID.class),any(byte[].class),anyList(),any(Instant.class));
        when(http.get(any(),anyString(),anyMap())).thenReturn(new OfficialHttpClient.Result(503,new byte[0],null,null),new OfficialHttpClient.Result(200,xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),null,null));
        var collector=new CollectorService(db,http,evidence,false,new SecConfiguration("","",""));collector.collect("FED");assertEquals(0,count("raw_evidence"));collector.collect("FED");collector.collect("FED");
        assertEquals(1,count("raw_evidence"));assertEquals(1,count("stream_outbox"));assertEquals(1,db.queryForObject("SELECT count(*) FROM ingestion_job WHERE status='FAILED'",Integer.class));assertEquals(2,db.queryForObject("SELECT count(*) FROM ingestion_job WHERE status='SUCCESS'",Integer.class));record("任务重试幂等与失败保留",Map.of("failed",1,"successful_attempts",2,"evidence",1,"outbox",1));
    }

    @Test void repeatedEvidenceDoesNotCreateRepeatedOutbox(){assertEquals(1,save(job()));assertEquals(0,save(job()));assertEquals(1,count("raw_evidence"));assertEquals(1,count("stream_outbox"));record("重复任务幂等",Map.of("evidence",1,"outbox",1));}

    @Test void conflictingOwnerIsLegallySkippedAndLeaseRecovers() throws Exception {
        var first=new CollectorGuard(db);var second=new CollectorGuard(db);try(var lease=first.acquire("FED")){assertNotNull(lease);db.update("UPDATE collector_lease SET expires_at=now()-interval '1 minute'");assertNull(second.acquire("FED"));assertEquals(0,count("ingestion_job"));}
        try(var recovered=second.acquire("FED")){assertNotNull(recovered);}assertEquals(0,count("collector_lease"));record("调度锁冲突与释放恢复",Map.of("duplicate_jobs",0,"leases_after_close",0));
    }

    @Test void schedulerRunsAgainAfterDatabaseCapacityRecovers() throws Exception {
        var stream=new StreamService(db,new ObjectMapper());var errors=new AtomicInteger();var successes=new AtomicInteger();var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("S69-R1隔离调度-");scheduler.setErrorHandler(e->{errors.incrementAndGet();record("调度真实数据库失败",failure(e));});scheduler.initialize();ScheduledFuture<?> task=null;
        try{try(var first=pool.getConnection();var second=pool.getConnection()){
            task=scheduler.scheduleWithFixedDelay(()->{stream.publish();successes.incrementAndGet();},Duration.ofMillis(100));long start=System.nanoTime();while(errors.get()==0&&System.nanoTime()-start<TimeUnit.SECONDS.toNanos(3))Thread.sleep(10);assertTrue(errors.get()>0);
        }
        long start=System.nanoTime();while(successes.get()==0&&System.nanoTime()-start<TimeUnit.SECONDS.toNanos(3))Thread.sleep(10);assertTrue(successes.get()>0);assertFalse(task.isDone());record("数据库恢复后真实固定延迟任务恢复",Map.of("failures",errors.get(),"successful_runs",successes.get()));}finally{if(task!=null)task.cancel(false);scheduler.shutdown();}
    }

    @Test void sseCursorIsNotAdvancedWhenDatabaseFailsAndOutboxIsSentAfterRecovery() throws Exception {
        save(job());var stream=new StreamService(db,new ObjectMapper().findAndRegisterModules());stream.connect(0,"HISTORY");
        try(var first=pool.getConnection();var second=pool.getConnection()){var e=assertThrows(Exception.class,stream::publish);record("SSE游标获取失败",failure(e));}
        assertEquals(1,stream.activeClients());assertEquals(1,count("stream_outbox"));assertEquals(0,count("stream_delivery"));stream.publish();assertEquals(1,db.queryForObject("SELECT count(*) FROM stream_delivery WHERE outcome='SENT'",Integer.class));stream.publish();assertEquals(1,count("stream_delivery"));stream.closeClients();record("恢复后SSE与Outbox不丢消息不重复",Map.of("outbox",1,"sent",1,"scope","隔离服务内部SSE；实际网络SSE另行测试"));
    }

    @Test void gracefulApplicationComponentsCloseAndNewPoolReplaysPersistedOutbox(){
        save(job());var stream=new StreamService(db,new ObjectMapper());stream.connect(0,"HISTORY");stream.closeClients();assertEquals("SERVER_SHUTDOWN",db.queryForObject("SELECT close_kind FROM stream_connection LIMIT 1",String.class));pool.close();pool=replacement();db.setDataSource(pool);assertEquals(1,count("stream_outbox"));var restored=new StreamService(db,new ObjectMapper().findAndRegisterModules());restored.connect(0,"HISTORY");restored.publish();assertEquals(1,count("stream_delivery"));restored.closeClients();record("组件正常关闭与重建恢复",Map.of("persisted_outbox",1,"replayed_deliveries",1,"limitation","本用例是组件生命周期；完整JVM关闭另行独立实例验证"));
    }

    @Test void missingSqlParameterTypeIsAlreadyFixedInMacroCatalog(){
        var e=assertThrows(Exception.class,()->db.queryForObject("SELECT ? IS NULL",Boolean.class,(Object)null));assertEquals("42P18",sqlException(e).getSQLState());assertDoesNotThrow(()->new cn.huankan.event.MacroChangeService(db).catalog(0,null));record("S65历史参数类型错误复现及既有修复核验",failure(e));
    }

    @Test void clientClosingImmediatelyAfterReceiveStillKeepsSentDeliveryRecord() throws Exception {
        save(job());var emitter=mock(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.class);
        var stream=new StreamService(db,new ObjectMapper().findAndRegisterModules()){@Override protected org.springframework.web.servlet.mvc.method.annotation.SseEmitter createEmitter(){return emitter;}};
        stream.connect(0,"HISTORY");UUID connection=stream.connectionId(emitter);
        doAnswer(invocation->{stream.clientClose(connection,"CLIENT_REQUESTED");return null;}).when(emitter).send(any(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder.class));
        stream.publish();assertEquals(1,db.queryForObject("SELECT count(*) FROM stream_delivery WHERE connection_id=? AND outcome='SENT'",Integer.class,connection));assertEquals(0,stream.activeClients());assertEquals(1,count("stream_outbox"));record("接收后立即关闭仍保留实际发送记录",Map.of("sent",1,"active_clients",0,"outbox",1));
    }

    @Test void originalDefaultPoolConfigurationIsMeasuredNotExpanded() throws Exception {
        try(var defaults=new HikariDataSource()){
            defaults.setJdbcUrl(base.getUrl()+"?currentSchema="+schema);defaults.setUsername(System.getenv("HUANKAN_TEST_DB_USER"));defaults.setPassword(System.getenv("HUANKAN_TEST_DB_PASSWORD"));defaults.setPoolName(schema+"_defaults");try(var connection=defaults.getConnection()){
                var measured=JdbcDiagnostics.pool(new JdbcTemplate(defaults));assertEquals(10,defaults.getMaximumPoolSize());assertEquals(30000,defaults.getConnectionTimeout());record("同版本Hikari默认配置实际测量",measured);
            }
        }
    }
}
