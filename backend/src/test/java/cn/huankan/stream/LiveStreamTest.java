package cn.huankan.stream;

import cn.huankan.collector.SourceAdapter.Item;
import cn.huankan.evidence.EvidenceService;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveStreamTest {
    static final String TOKEN="isolated-sse-test-only-access-token";
    static final String SCHEMA="sse_"+UUID.randomUUID().toString().replace("-","");
    static DriverManagerDataSource base;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("HUANKAN_TEST_DB_URL");
        if(url==null||!url.matches("jdbc:postgresql://[^/]+/huankan_test"))throw new IllegalStateException("SSE集成测试需要明确的huankan_test隔离数据库");
        base=new DriverManagerDataSource(url,System.getenv("HUANKAN_TEST_DB_USER"),System.getenv("HUANKAN_TEST_DB_PASSWORD"));
        new JdbcTemplate(base).execute("CREATE SCHEMA "+SCHEMA);
        properties.add("spring.datasource.url",()->url+"?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->System.getenv("HUANKAN_TEST_DB_USER"));
        properties.add("spring.datasource.password",()->System.getenv("HUANKAN_TEST_DB_PASSWORD"));
        properties.add("huankan.token",()->TOKEN);properties.add("huankan.collector-enabled",()->false);
    }
    @Autowired JdbcTemplate db; @Autowired EvidenceService evidence; @LocalServerPort int port;
    @Autowired StreamService stream;
    @AfterEach void closeSubscribers(){stream.closeClients();}
    @AfterAll static void cleanup(){if(base!=null)new JdbcTemplate(base).execute("DROP SCHEMA "+SCHEMA+" CASCADE");}
    record Connection(BufferedReader reader,InputStream input) implements AutoCloseable {
        public void close() throws IOException {input.close();}
        String nextId() throws Exception {
            return CompletableFuture.supplyAsync(()->{try{String line;while((line=reader.readLine())!=null){if(line.startsWith("id:"))return line.substring(3).strip();}return "EOF";}catch(IOException e){throw new CompletionException(e);}}).get(10,TimeUnit.SECONDS);
        }
    }
    Connection connect(long cursor)throws Exception{return connect(cursor,"HISTORY");}
    Connection connect(long cursor,String scope)throws Exception{
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/stream?scope="+scope)).header("Authorization","Bearer "+TOKEN).header("Last-Event-ID",Long.toString(cursor)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200,response.statusCode());return new Connection(new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8)),response.body());
    }
    long insert(String document,Instant published)throws Exception{
        UUID job=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'FED','RUNNING')",job);
        evidence.persist("FED",job,("ISOLATED SSE FIXTURE "+document).getBytes(),List.of(new Item(document,"隔离推送测试 "+document,"https://www.federalreserve.gov/test-only",published,"测试")),Instant.now());
        return db.queryForObject("SELECT max(id) FROM stream_outbox",Long.class);
    }
    @Test void realHttpSseSupportsTwoClientsDisconnectAndDurableReplay()throws Exception{
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='ISOLATED SSE TEST ONLY' WHERE source_id='FED'");
        long historical=insert("historical",Instant.parse("2025-01-01T00:00:00Z"));
        db.update("UPDATE source_cursor SET monitoring_started_at=now()-interval '1 minute' WHERE source_id='FED'");
        try(var one=connect(0);var two=connect(0)){
            assertEquals(Long.toString(historical),one.nextId());assertEquals(Long.toString(historical),two.nextId());
            long next=insert("new",Instant.now());
            assertEquals(Long.toString(next),one.nextId());assertEquals(Long.toString(next),two.nextId());
            assertEquals("NEW_EVENT",db.queryForObject("SELECT notification_kind FROM stream_outbox WHERE id=?",String.class,next));
        }
        long disconnected=insert("while-disconnected",Instant.now());
        try(var replay=connect(historical)){assertEquals(Long.toString(historical+1),replay.nextId());assertEquals(Long.toString(disconnected),replay.nextId());}
        assertEquals(3,db.queryForObject("SELECT count(*) FROM stream_outbox",Integer.class));
        for(int wait=0;wait<20 && db.queryForObject("SELECT count(*) FROM stream_delivery WHERE outcome='SENT'",Integer.class)<6;wait++)Thread.sleep(50);
        assertTrue(db.queryForObject("SELECT count(*) FROM stream_delivery WHERE outcome='SENT'",Integer.class)>=6);
        var delivery=db.queryForObject("SELECT id FROM stream_delivery WHERE outcome='SENT' LIMIT 1",UUID.class);
        stream.acknowledge(delivery,Instant.now());stream.acknowledge(delivery,Instant.now());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM stream_delivery WHERE acknowledged_at IS NOT NULL",Integer.class));
        try(var qualified=connect(0,"OFFICIAL")){assertEquals(Long.toString(historical+1),qualified.nextId());assertEquals(Long.toString(disconnected),qualified.nextId());}
        assertEquals(1,db.queryForObject("SELECT count(*) FROM notification_qualification WHERE NOT official_eligible",Integer.class));
        // The actual HTTP subscription must apply persisted owner rules before pagination.
        String owner=cn.huankan.event.WatchService.owner("Bearer "+TOKEN);
        db.update("INSERT INTO user_watch(id,owner_key,kind,value,label) VALUES(?,?,'SOURCE','FED','隔离关注规则')",UUID.randomUUID(),owner);
        db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='ISOLATED SSE TEST ONLY' WHERE source_id='ECB'");
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/stream?scope=HISTORY&watchOnly=true")).header("Last-Event-ID",Long.toString(disconnected)).header("Authorization","Bearer "+TOKEN).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200,response.statusCode());
        try(var watched=new Connection(new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8)),response.body())){
            UUID otherJob=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status) VALUES(?,'ECB','RUNNING')",otherJob);
            evidence.persist("ECB",otherJob,"ISOLATED UNWATCHED ECB".getBytes(),List.of(new Item("isolated-unwatched","隔离未关注来源","https://www.ecb.europa.eu/test-only",Instant.parse("2025-01-01T00:00:00Z"),"测试")),Instant.now());
            long selected=insert("isolated-watched-fed",Instant.parse("2025-01-01T00:00:00Z"));
            assertEquals(Long.toString(selected),watched.nextId());
            assertEquals(owner,db.queryForObject("SELECT watch_owner_key FROM stream_connection WHERE watch_owner_key IS NOT NULL ORDER BY opened_at DESC LIMIT 1",String.class));
        }
    }
}
