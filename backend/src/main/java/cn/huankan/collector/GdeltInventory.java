package cn.huankan.collector;

import java.net.URI;
import java.net.http.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.security.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GdeltInventory {
    private final JdbcTemplate db;private final GdeltDownloader http;private final CollectorGuard guard;
    public GdeltInventory(JdbcTemplate db,GdeltDownloader http,CollectorGuard guard){this.db=db;this.http=http;this.guard=guard;}
    public void discover() throws Exception {
        byte[] latest=http.get(URI.create("https://data.gdeltproject.org/gdeltv2/lastupdate.txt"),0,null);
        var entries=GdeltIndex.latest(latest);
        db.update("INSERT INTO gdelt_index_observation(url,sha256,raw_latest,bytes,records) VALUES(?,?,?,?,?)","https://data.gdeltproject.org/gdeltv2/lastupdate.txt",cn.huankan.evidence.EvidenceService.hash(latest),latest,latest.length,entries.size());
        for(var entry:entries)if(entry.kind().equals("gkg"))register(entry,"REALTIME");
        String earliest=db.queryForObject("SELECT COALESCE((SELECT inventory_checked_until FROM source_cursor WHERE source_id='GDELT'),(SELECT min(batch_id) FROM gdelt_batch))",String.class),latestBatch=entries.getFirst().batch();
        if(earliest!=null){var slots=GdeltIndex.slots(earliest,latestBatch,672);for(String slot:slots)db.update("INSERT INTO gdelt_gap(batch_id) SELECT ? WHERE NOT EXISTS(SELECT 1 FROM gdelt_batch WHERE batch_id=?) ON CONFLICT DO NOTHING",slot,slot);if(!slots.isEmpty())db.update("INSERT INTO source_cursor(source_id,inventory_checked_until) VALUES('GDELT',?) ON CONFLICT(source_id) DO UPDATE SET inventory_checked_until=excluded.inventory_checked_until",slots.getLast());}
        boolean gaps=Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gdelt_gap WHERE state<>'RESOLVED')",Boolean.class));
        if(gaps&&!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM gdelt_index_observation WHERE url LIKE '%masterfilelist.txt' AND fetched_at>now()-interval '6 hours')",Boolean.class))){String floor=db.queryForObject("SELECT min(batch_id) FROM gdelt_gap WHERE state<>'RESOLVED'",String.class);var window=GdeltIndex.slots(floor,latestBatch,672);history(floor,window.isEmpty()?latestBatch:window.getLast());}
        db.update("UPDATE gdelt_gap g SET state='RESOLVED',last_checked_at=now() FROM gdelt_batch b WHERE b.batch_id=g.batch_id AND b.status='SUCCESS'");
    }
    public void register(GdeltIndex.Entry e,String kind){
        if(e.bytes()>10*1024*1024)return;
        db.update("INSERT INTO gdelt_batch(batch_id,file_url,expected_bytes,expected_md5,status,discovery_kind,availability,retry_at) VALUES(?,?,?,?,'DOWNLOADING',?,'PUBLISHED_NOT_AVAILABLE',?) ON CONFLICT DO NOTHING",e.batch(),e.url(),e.bytes(),e.md5(),kind,java.sql.Timestamp.from(e.time()));
        db.update("UPDATE gdelt_gap SET state='PUBLISHED_NOT_AVAILABLE',last_checked_at=now() WHERE batch_id=? AND state<>'RESOLVED'",e.batch());
    }
    /** One bounded streaming scan per six hours; only requested time-window entries are retained. */
    private void history(String from,String to)throws Exception{
        String url="https://data.gdeltproject.org/gdeltv2/masterfilelist.txt";guard.request("data.gdeltproject.org");
        Instant started=Instant.now();Integer status=null;String error=null;long bytes=0;int records=0;var hash=MessageDigest.getInstance("SHA-256");
        // Journal the scan start so failed scans also enforce the six-hour traffic budget.
        Long observation=db.queryForObject("INSERT INTO gdelt_index_observation(url,sha256,bytes,records,complete) VALUES(?,repeat('0',64),0,0,false) RETURNING id",Long.class,url);
        try{
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
            var response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("User-Agent","HUANKAN/0.3 local metadata reader").GET().build(),HttpResponse.BodyHandlers.ofInputStream());status=response.statusCode();
            try(var input=new BufferedInputStream(response.body(),65536);var bodyDeadline=BodyDeadline.arm(input,Duration.ofMinutes(3))){
                if(status!=200)throw new IOException("GDELT_HTTP_"+status);
                var line=new ByteArrayOutputStream();int c;long deadline=System.nanoTime()+Duration.ofMinutes(3).toNanos();
                while((c=input.read())!=-1){bytes++;hash.update((byte)c);if(bytes>512L*1024*1024||System.nanoTime()>deadline)throw new IOException("历史索引超过512MiB或三分钟扫描预算");
                    if(c=='\n'){
                        String text=line.toString(java.nio.charset.StandardCharsets.US_ASCII);line.reset();
                        if(text.strip().endsWith(".gkg.csv.zip")){var e=GdeltIndex.line(text);if(e.batch().compareTo(from)>=0&&e.batch().compareTo(to)<=0){register(e,"HISTORICAL_BACKFILL");records++;}}
                    }else{if(line.size()>1024)throw new IOException("历史索引行超限");line.write(c);}
                }
                bodyDeadline.verify();long expected=response.headers().firstValueAsLong("Content-Length").orElse(-1);if(expected>=0&&bytes!=expected)throw new IOException("历史索引响应未完整，不能判定缺失批次");
            }
            db.update("UPDATE gdelt_gap SET state='MISSING_HISTORICAL_BATCH',last_checked_at=now() WHERE state='NOT_PUBLISHED' AND batch_id<?",to);
            db.update("UPDATE gdelt_index_observation SET sha256=?,bytes=?,records=?,complete=true WHERE id=?",HexFormat.of().formatHex(hash.digest()),bytes,records,observation);
        }catch(Exception e){error=CollectorGuard.failure(e)+": "+e.getMessage();db.update("UPDATE gdelt_index_observation SET sha256=?,bytes=?,records=?,error=? WHERE id=?",HexFormat.of().formatHex(hash.digest()),bytes,records,error,observation);throw e;}
        finally{db.update("INSERT INTO http_request_observation(source_id,endpoint,attempted_at,completed_at,status,duration_ms,response_bytes,error,failure_kind) VALUES('GDELT',?,?,now(),?,?,?,?,?)",url,java.sql.Timestamp.from(started),status,Duration.between(started,Instant.now()).toMillis(),bytes,error,error==null?null:"HISTORICAL_INDEX_FAILURE");}
    }
}
