package cn.huankan.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class IsolatedReliabilityDatabase {
    protected DriverManagerDataSource base;
    protected HikariDataSource pool;
    protected JdbcTemplate db;
    protected TransactionTemplate tx;
    protected String schema;
    private boolean preserveR3Records;

    @BeforeEach void isolatedDatabase() throws Exception {
        String url=System.getenv("HUANKAN_TEST_DB_URL");
        if(!"jdbc:postgresql://127.0.0.1:55432/huankan_test".equals(url))throw new IllegalStateException("仅允许已核对的55432/huankan_test独立实例");
        base=new DriverManagerDataSource(url,System.getenv("HUANKAN_TEST_DB_USER"),System.getenv("HUANKAN_TEST_DB_PASSWORD"));
        var identity=new JdbcTemplate(base).queryForMap("SELECT current_database() database,inet_server_port() port,current_user username");
        if(!"huankan_test".equals(identity.get("database"))||!Integer.valueOf(55432).equals(identity.get("port")))throw new IllegalStateException("实际测试数据库身份不匹配");
        schema="r1_it_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(base).execute("CREATE SCHEMA "+schema);
        // Migrations use their own short-lived setup connections. Fault injection only
        // starts after setup; the application pool keeps its original 300ms threshold.
        Flyway.configure().dataSource(new DriverManagerDataSource(url+"?currentSchema="+schema,System.getenv("HUANKAN_TEST_DB_USER"),System.getenv("HUANKAN_TEST_DB_PASSWORD"))).defaultSchema(schema).load().migrate();
        pool=new HikariDataSource();pool.setJdbcUrl(url+"?currentSchema="+schema);pool.setUsername(System.getenv("HUANKAN_TEST_DB_USER"));pool.setPassword(System.getenv("HUANKAN_TEST_DB_PASSWORD"));
        pool.setMaximumPoolSize(2);pool.setMinimumIdle(1);pool.setConnectionTimeout(300);pool.setValidationTimeout(250);pool.setPoolName(schema);
        pool.addDataSourceProperty("ApplicationName",schema);
        long warmStarted=System.nanoTime();
        try(var first=pool.getConnection()){
            // Wait for the second configured slot to exist before injecting saturation.
            // Acquisition remains 300ms; no dynamic timeout or capacity adjustment.
            while(pool.getHikariPoolMXBean().getTotalConnections()<2&&System.nanoTime()-warmStarted<java.util.concurrent.TimeUnit.SECONDS.toNanos(30))Thread.sleep(10);
            if(pool.getHikariPoolMXBean().getTotalConnections()<2)throw new IllegalStateException("故障注入准备阶段未形成两个连接，不执行注入");
            try(var second=pool.getConnection()){}
        }
        record("S69-R3/故障池准备与预热",Map.of("stage","SETUP_NOT_INJECTION","maximum",2,"total",pool.getHikariPoolMXBean().getTotalConnections(),"connection_timeout_ms",pool.getConnectionTimeout(),"warmup_ms",(System.nanoTime()-warmStarted)/1_000_000));
        db=new JdbcTemplate(pool);tx=new TransactionTemplate(new DataSourceTransactionManager(pool));
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='S69-R1 ISOLATED ONLY' WHERE source_id='FED'");
        db.update("UPDATE source_registry SET enabled=true WHERE id='FED'");
    }
    @AfterEach void removeOnlyOurSchema(){
        if(preserveR3Records&&base!=null&&schema!=null&&schema.matches("r1_it_[a-f0-9]{32}")){
            // Preserve complete isolated failure records and committed provenance before fixture cleanup.
            try {
                var exports=new LinkedHashMap<String,Object>();var mapper=new ObjectMapper().findAndRegisterModules();
                exports.put("database","huankan_test");exports.put("port",55432);exports.put("schema",schema);exports.put("at",java.time.Instant.now().toString());
                var diagnostic=new JdbcTemplate(base);
                for(String table:List.of("ingestion_job","ingestion_quarantine","gdelt_batch","collector_batch_commit","raw_evidence","stream_outbox","official_response","response_evidence","macro_observation")){
                    String contents=diagnostic.queryForObject("SELECT coalesce(json_agg(t),'[]'::json)::text FROM "+schema+"."+table+" t",String.class);exports.put(table,mapper.readTree(contents));
                }
                var output=java.nio.file.Path.of("../docs/evidence/s69-r3-isolated-records-"+schema+".json");
                java.nio.file.Files.writeString(output,mapper.writeValueAsString(exports),java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE_NEW);
                record("S69-R3/隔离完整记录导出",Map.of("file",output.toString(),"cleanup","仅删除本测试随机schema；导出包含失败任务、证据和Outbox完整记录"));
            }catch(Exception failure){throw new IllegalStateException("隔离证据导出失败，保留测试schema",failure);}
        }
        if(pool!=null)pool.close();if(base!=null&&schema!=null&&schema.matches("r1_it_[a-f0-9]{32}"))new JdbcTemplate(base).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    protected void record(String scenario,Object data){try{System.out.println("S69_R1 "+new ObjectMapper().findAndRegisterModules().writeValueAsString(Map.of("at",java.time.Instant.now().toString(),"scenario",scenario,"schema",schema,"data",data)));}catch(Exception e){throw new IllegalStateException(e);}}
    protected int count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    /** Normal-load scenarios must not inherit the deliberately tiny fault timeout. */
    protected void normalPool(int capacity) throws Exception {
        preserveR3Records=true;
        String url=pool.getJdbcUrl();pool.close();
        pool=new HikariDataSource();pool.setJdbcUrl(url);pool.setUsername(System.getenv("HUANKAN_TEST_DB_USER"));pool.setPassword(System.getenv("HUANKAN_TEST_DB_PASSWORD"));
        pool.setMaximumPoolSize(capacity);pool.setMinimumIdle(capacity);pool.setPoolName(schema+"_normal");pool.addDataSourceProperty("ApplicationName",schema);
        var warm=new ArrayList<java.sql.Connection>();try{for(int n=0;n<capacity;n++)warm.add(pool.getConnection());}finally{for(var connection:warm)connection.close();}
        db.setDataSource(pool);tx=new TransactionTemplate(new DataSourceTransactionManager(pool));
    }
}
