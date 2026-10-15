package cn.huankan.collector;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GdeltDownloader {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CollectorGuard guard;
    private final JdbcTemplate db;
    private Path directory=Path.of("runtime","gdelt-downloads").toAbsolutePath();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
    public GdeltDownloader(JdbcTemplate db){this.db=db;}
    static void validate(URI uri){if(!"https".equals(uri.getScheme())||!"data.gdeltproject.org".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().matches("/gdeltv2/(lastupdate[.]txt|[0-9]{14}[.]gkg[.]csv[.]zip)"))throw new IllegalArgumentException("GDELT下载地址不在白名单");}
    public synchronized byte[] get(URI uri,long expected,String batch) throws Exception {
        validate(uri);if(expected>10*1024*1024||expected<0)throw new IOException("批次下载大小超限");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM source_cursor WHERE source_id='GDELT' AND retry_after_at>now())",Boolean.class)))throw new IOException("GDELT限流冷却尚未结束，未发送HTTP请求");
        Path part=null;long offset=0;
        if(batch!=null){if(!batch.matches("[0-9]{14}"))throw new IllegalArgumentException("批次标识无效");Files.createDirectories(directory);part=directory.resolve(batch+".part");if(Files.exists(part))offset=Files.size(part);if(offset>expected){Files.delete(part);offset=0;}}
        // A crash after the final byte must not issue an unsatisfiable bytes=size- request.
        if(part!=null&&offset==expected)return Files.readAllBytes(part);
        if(guard!=null)guard.request(uri.getHost());
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).header("User-Agent","HUANKAN/0.2 (local metadata research reader)");
        if(offset>0)request.header("Range","bytes="+offset+"-");
        long start=System.nanoTime();Instant attempted=Instant.now();Integer status=null;String etag=null,modified=null,error=null;long received=0;
        try {
            var response=client.send(request.GET().build(),HttpResponse.BodyHandlers.ofInputStream());status=response.statusCode();etag=response.headers().firstValue("ETag").orElse(null);modified=response.headers().firstValue("Last-Modified").orElse(null);
            try(var input=response.body();var deadline=BodyDeadline.arm(input,Duration.ofSeconds(60))){
                if(status==429)db.update("INSERT INTO source_cursor(source_id,retry_after_at) VALUES('GDELT',?) ON CONFLICT(source_id) DO UPDATE SET retry_after_at=excluded.retry_after_at",java.sql.Timestamp.from(Instant.ofEpochMilli(OfficialHttpClient.retryAt(response.headers().firstValue("Retry-After").orElse(null)))));
                if(status!=200&&status!=206)throw new IOException("GDELT_HTTP_"+status);
                if(status==206&&(offset==0||!response.headers().firstValue("Content-Range").orElse("").startsWith("bytes "+offset+"-")))throw new IOException("续传范围不匹配");
                if(status==200)offset=0;
                long limit=batch==null?65536:expected;
                var memory=new ByteArrayOutputStream();
                try(OutputStream output=part==null?memory:Files.newOutputStream(part,StandardOpenOption.CREATE,offset>0?StandardOpenOption.APPEND:StandardOpenOption.TRUNCATE_EXISTING)){
                    byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1){received+=read;if(offset+received>limit)throw new IOException("实际下载字节超过允许上限");output.write(buffer,0,read);}
                }
                byte[] data=part==null?memory.toByteArray():Files.readAllBytes(part);
                deadline.verify();
                if(batch!=null&&data.length!=expected)throw new IOException("下载未完整，将保留断点重试");
                return data;
            }
        }catch(Exception e){error=e.getClass().getSimpleName()+": "+e.getMessage();throw e;}
        finally{db.update("INSERT INTO http_request_observation(source_id,endpoint,attempted_at,completed_at,status,duration_ms,etag,last_modified,response_bytes,error,failure_kind) VALUES('GDELT',?,?,now(),?,?,?,?,?,?,?)",uri.toString(),java.sql.Timestamp.from(attempted),status,(System.nanoTime()-start)/1000000,etag,modified,received,error,error==null?null:CollectorGuard.failure(new IOException(error)));}
    }
    public void removeCompleted(String batch)throws IOException{if(batch.matches("[0-9]{14}"))Files.deleteIfExists(directory.resolve(batch+".part"));}
}
