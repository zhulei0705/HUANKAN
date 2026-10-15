package cn.huankan.reliability;

import cn.huankan.collector.*;
import cn.huankan.evidence.EvidenceService;
import cn.huankan.event.*;
import cn.huankan.stream.StreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectionLifecycleLoadTest extends IsolatedReliabilityDatabase {
    @Test void frozenR2CollectorReproducesWholeBatchRollbackAndHeartbeatRowLock() throws Exception {
        normalPool(4);
        java.nio.file.Path jar=java.nio.file.Path.of("target/huankan-s69-r2-candidate-0.1.0.jar");
        assertEquals("b624c1ec4c6ba658f56ab847881a811c3e42acdaca4e307a8343e43535345436",EvidenceService.hash(java.nio.file.Files.readAllBytes(jar)));
        byte[] code;try(var archive=new java.util.jar.JarFile(jar.toFile())){try(var input=archive.getInputStream(archive.getJarEntry("BOOT-INF/classes/cn/huankan/collector/GdeltCollector.class"))){code=input.readAllBytes();}}
        Class<?> baseline=new ClassLoader(getClass().getClassLoader()){
            Class<?> baseline(){return defineClass("cn.huankan.collector.GdeltCollector",code,0,code.length);}
        }.baseline();
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='S69-R3 ISOLATED ONLY' WHERE source_id='GDELT'");db.update("UPDATE source_registry SET enabled=true WHERE id='GDELT'");
        String batch="20261001000000";byte[] zip=zip(batch,120);String md5=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(zip));
        db.update("INSERT INTO gdelt_batch(batch_id,file_url,expected_bytes,expected_md5,raw_zip,sha256,status,availability) VALUES(?,?,?,?,?,?,'PROCESSING','AVAILABLE')",batch,"https://data.gdeltproject.org/gdeltv2/"+batch+".gkg.csv.zip",zip.length,md5,zip,EvidenceService.hash(zip));
        var evidence=spy(new EvidenceService(db));var calls=new AtomicInteger();var lockState=new AtomicReference<String>();
        doAnswer(inv->{int call=calls.incrementAndGet();if(call==51){
            var diagnostic=new org.springframework.jdbc.core.JdbcTemplate(base);
            var failure=assertThrows(Exception.class,()->new TransactionTemplate(new DataSourceTransactionManager(base)).executeWithoutResult(s->{diagnostic.execute("SET LOCAL lock_timeout='200ms'");diagnostic.update("UPDATE "+schema+".collector_lease SET heartbeat_at=clock_timestamp() WHERE source_id='GDELT'");}));
            Throwable cause=failure;while(cause!=null&&!(cause instanceof java.sql.SQLException))cause=cause.getCause();assertNotNull(cause);lockState.set(((java.sql.SQLException)cause).getSQLState());assertEquals("55P03",lockState.get());
            assertEquals(0,diagnostic.queryForObject("SELECT completed_lines FROM "+schema+".gdelt_batch WHERE batch_id=?",Integer.class,batch));
        }if(call==56)throw new IllegalStateException("【隔离故障注入】第二段中断");return inv.callRealMethod();}).when(evidence).persist(anyString(),any(),any(byte[].class),anyList(),any());
        Object collector=baseline.getConstructor(org.springframework.jdbc.core.JdbcTemplate.class,GdeltDownloader.class,EvidenceService.class,org.springframework.transaction.PlatformTransactionManager.class,CollectorGuard.class,GdeltInventory.class)
            .newInstance(db,mock(GdeltDownloader.class),evidence,new DataSourceTransactionManager(pool),new CollectorGuard(db),mock(GdeltInventory.class));baseline.getMethod("collect").invoke(collector);
        assertEquals(0,count("raw_evidence"));assertEquals(0,count("stream_outbox"));assertEquals(0,db.queryForObject("SELECT completed_lines FROM gdelt_batch WHERE batch_id=?",Integer.class,batch));
        assertEquals("FAILED",db.queryForObject("SELECT status FROM ingestion_job WHERE source_id='GDELT'",String.class));assertEquals(0,count("collector_lease"));
        record("S69-R3/冻结R2实际采集器对照",Map.of("jar_sha256",EvidenceService.hash(java.nio.file.Files.readAllBytes(jar)),"heartbeat_row_lock_state",lockState.get(),"checkpoint_after_failure",0,"committed_evidence",0,"committed_outbox",0,"intentional_fault",true,"failure_retained",1));
    }
    @Test void sameBoundedWorkloadComparesParentTransactionsWithChunks() throws Exception {
        normalPool(10);
        var before=load("整改前整批事务",125,"before");
        var after=load("整改后50条事务",50,"after");
        record("S69-R3/有界正常负载前后比较",Map.of("before",before,"after",after,"pool_max",10,"connection_timeout_ms",pool.getConnectionTimeout(),"fault_injection",false,
            "limitation","四个证据写入任务及两个查询/SSE读取任务；不等同于全部正式采集HTTP和16万历史数据容量"));
        assertEquals(500,before.get("inserted"));assertEquals(500,after.get("inserted"));
        assertEquals(0,before.get("unexpected_errors"));assertEquals(0,after.get("unexpected_errors"));
        assertEquals(1000,count("raw_evidence"));assertEquals(1000,count("stream_outbox"));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM (SELECT evidence_id,count(*) FROM stream_outbox GROUP BY evidence_id HAVING count(*)<>1)x",Integer.class));
    }
    private Map<String,Object> load(String name,int chunk,String prefix) throws Exception {
        var metrics=new SimpleMeterRegistry();var observed=new ObservedDataSource(pool,()->metrics);db.setDataSource(observed);
        tx=new TransactionTemplate(new DataSourceTransactionManager(observed));
        var evidence=new EvidenceService(db);var executor=Executors.newFixedThreadPool(6);var monitor=Executors.newSingleThreadScheduledExecutor();
        var active=new AtomicInteger();var pending=new AtomicInteger();var reads=new AtomicInteger();var errors=new AtomicInteger();var inserted=new AtomicInteger();
        var samples=Collections.synchronizedList(new ArrayList<Map<String,Object>>());var done=new AtomicBoolean();
        long started=System.nanoTime();
        var sampling=monitor.scheduleAtFixedRate(()->{var p=pool.getHikariPoolMXBean();active.accumulateAndGet(p.getActiveConnections(),Math::max);pending.accumulateAndGet(p.getThreadsAwaitingConnection(),Math::max);
            samples.add(Map.of("elapsed_ms",(System.nanoTime()-started)/1_000_000,"active",p.getActiveConnections(),"idle",p.getIdleConnections(),"pending",p.getThreadsAwaitingConnection()));},0,10,TimeUnit.MILLISECONDS);
        try {
            var readerTasks=new ArrayList<Future<?>>();
            for(int n=0;n<2;n++)readerTasks.add(executor.submit(()->{while(!done.get())try{new QueryService(db).events("","FED",0,"","","published");new StreamService(db,new ObjectMapper()).publish();reads.incrementAndGet();Thread.sleep(20);}catch(Exception e){errors.incrementAndGet();}}));
            var writers=new ArrayList<Future<?>>();
            for(int n=0;n<4;n++){final int worker=n;writers.add(executor.submit(()->{
                try(var context=new DiagnosticContext(name+"/采集"+worker)){
                    UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FED','RUNNING')",job);
                    for(int offset=0;offset<125;offset+=chunk){int from=offset,to=Math.min(offset+chunk,125);
                        int committed=tx.execute(s->{int count=0;for(int i=from;i<to;i++){String key=prefix+"-"+worker+"-"+i;
                            var item=new SourceAdapter.Item(key,"【隔离测试】Federal Reserve interest rate "+key,"https://www.federalreserve.gov/fixture/"+key,null,"隔离测试");
                            count+=evidence.persist("FED",job,key.getBytes(StandardCharsets.UTF_8),List.of(item),Instant.now());
                        }return count;});inserted.addAndGet(committed);
                    }db.update("UPDATE ingestion_job SET status='SUCCESS',new_count=125,fetched_count=125,finished_at=now() WHERE id=?",job);
                }catch(Exception e){errors.incrementAndGet();throw new IllegalStateException(e);}
            }));}
            for(var task:writers)task.get(90,TimeUnit.SECONDS);done.set(true);for(var task:readerTasks)task.get(10,TimeUnit.SECONDS);
        }finally{done.set(true);sampling.cancel(false);monitor.shutdownNow();executor.shutdownNow();}
        double elapsed=(System.nanoTime()-started)/1e9;
        var transactions=metrics.get("huankan.jdbc.transaction").tag("outcome","commit").timer();
        assertTrue(observed.holders().isEmpty());
        return Map.ofEntries(Map.entry("elapsed_seconds",elapsed),Map.entry("inserted",inserted.get()),Map.entry("read_cycles",reads.get()),
            Map.entry("unexpected_errors",errors.get()),Map.entry("rows_per_second",inserted.get()/elapsed),Map.entry("max_active",active.get()),Map.entry("max_pending",pending.get()),
            Map.entry("transaction_count",transactions.count()),Map.entry("transaction_max_ms",transactions.max(TimeUnit.MILLISECONDS)),
            Map.entry("transaction_total_ms",transactions.totalTime(TimeUnit.MILLISECONDS)),Map.entry("acquire_total_ms",metrics.get("huankan.jdbc.acquire").tag("outcome","success").timer().totalTime(TimeUnit.MILLISECONDS)),
            Map.entry("query_total_ms",metrics.get("huankan.jdbc.sql").tag("outcome","success").timer().totalTime(TimeUnit.MILLISECONDS)),Map.entry("samples",samples),
            Map.entry("outbox_retained",count("stream_outbox")),Map.entry("holders_after",observed.holders()));
    }

    @Test void realGdeltPartialFailureKeepsCommittedCheckpointAndRetriesWithoutDuplicates() throws Exception {
        normalPool(4);
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='S69-R3 ISOLATED ONLY' WHERE source_id='GDELT'");
        db.update("UPDATE source_registry SET enabled=true WHERE id='GDELT'");
        String batch="20261001000000";byte[] zip=zip(batch,120);
        String md5=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5").digest(zip));
        db.update("INSERT INTO gdelt_batch(batch_id,file_url,expected_bytes,expected_md5,raw_zip,sha256,status,availability) VALUES(?,?,?,?,?,?,'PROCESSING','AVAILABLE')",batch,"https://data.gdeltproject.org/gdeltv2/"+batch+".gkg.csv.zip",zip.length,md5,zip,EvidenceService.hash(zip));
        var evidence=spy(new EvidenceService(db));var calls=new AtomicInteger();
        doAnswer(inv->{int call=calls.incrementAndGet();
            if(call==51){
                var diagnostic=new org.springframework.jdbc.core.JdbcTemplate(base);
                assertEquals(50,diagnostic.queryForObject("SELECT completed_lines FROM "+schema+".gdelt_batch WHERE batch_id=?",Integer.class,batch));
                new TransactionTemplate(new DataSourceTransactionManager(base)).executeWithoutResult(s->{
                    diagnostic.execute("SET LOCAL lock_timeout='200ms'");
                    diagnostic.update("UPDATE "+schema+".collector_lease SET heartbeat_at=clock_timestamp() WHERE source_id='GDELT'");
                });
            }
            if(call==56)throw new IllegalStateException("【隔离故障注入】第二分批中断");return inv.callRealMethod();}).when(evidence).persist(anyString(),any(),any(byte[].class),anyList(),any());
        var inventory=mock(GdeltInventory.class);var collector=new GdeltCollector(db,mock(GdeltDownloader.class),evidence,new DataSourceTransactionManager(pool),new CollectorGuard(db),inventory);
        collector.collect();
        assertEquals(50,count("raw_evidence"));assertEquals(50,count("stream_outbox"));
        assertEquals(50,db.queryForObject("SELECT completed_lines FROM gdelt_batch WHERE batch_id=?",Integer.class,batch));
        var failed=db.queryForMap("SELECT status,new_count,fetched_count FROM ingestion_job WHERE source_id='GDELT'");assertEquals("FAILED",failed.get("status"));assertEquals(50,failed.get("new_count"));
        db.update("UPDATE gdelt_batch SET retry_at=now() WHERE batch_id=?",batch);collector.collect();
        assertEquals(120,count("raw_evidence"));assertEquals(120,count("stream_outbox"));assertEquals(120,count("aggregated_dependency"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM ingestion_job WHERE status='FAILED'",Integer.class));
        assertEquals(70,db.queryForObject("SELECT new_count FROM ingestion_job WHERE status='SUCCESS'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM collection_task_accounting WHERE counter_state='COUNTER_MISMATCH'",Integer.class));
        assertEquals(0,count("collector_lease"));assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());
        record("S69-R3/GDELT真实分批失败与恢复",Map.of("injected_failure",failed,"checkpoint_after_failure",50,"recovered_evidence",120,"outbox",120,"failure_retained",1,"retry_new",70,"batch_receipts",db.queryForList("SELECT fetched,inserted,duplicates FROM collector_batch_commit")));
    }
    private byte[] zip(String batch,int size) throws Exception {
        var data=new StringBuilder();for(int n=1;n<=size;n++){String[] f=new String[27];Arrays.fill(f,"");f[0]=batch+"-"+n;f[1]=batch;f[3]="隔离发布者";f[4]="https://example.invalid/r3/"+n;f[7]="ECON_INTEREST_RATE";data.append(String.join("\t",f)).append('\n');}
        var bytes=new ByteArrayOutputStream();try(var out=new ZipOutputStream(bytes)){out.putNextEntry(new ZipEntry(batch+".gkg.csv"));out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();}return bytes.toByteArray();
    }
}
