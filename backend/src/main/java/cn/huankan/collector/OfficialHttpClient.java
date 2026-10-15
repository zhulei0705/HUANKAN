package cn.huankan.collector;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@Component
public class OfficialHttpClient {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CollectorGuard guard;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.jdbc.core.JdbcTemplate observations;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.transaction.PlatformTransactionManager observationTransactions;
    private void observe(URI uri,long started,long elapsed,Result result,Exception error) {
        if(observations==null)return;
        String source=uri.getHost().endsWith("sec.gov")?"SEC":uri.getHost().equals("www.federalreserve.gov")?"FED":uri.getHost().equals("www.ecb.europa.eu")?"ECB":uri.getHost().equals("www.stats.gov.cn")?"NBS":uri.getHost().equals("data-api.ecb.europa.eu")?"ECB_DATA":uri.getHost().equals("api.gleif.org")?"GLEIF":null;
        Runnable write=()->observations.update("INSERT INTO http_request_observation(source_id,endpoint,attempted_at,completed_at,status,duration_ms,etag,last_modified,response_bytes,error,failure_kind) VALUES(?,?,?,now(),?,?,?,?,?,?,?)",source,uri.toString(),java.sql.Timestamp.from(java.time.Instant.ofEpochMilli(started)),result==null?null:result.status(),elapsed,result==null?null:result.etag(),result==null?null:result.modified(),result==null?null:result.body().length,error==null?null:error.getClass().getSimpleName(),error==null?null:CollectorGuard.failure(error));
        if(observationTransactions==null)write.run();
        else {var transaction=new org.springframework.transaction.support.TransactionTemplate(observationTransactions);transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);transaction.executeWithoutResult(s->write.run());}
    }
    public record Result(int status, byte[] body, String etag, String modified) {}
    private final WebClient client = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(HttpClient.create().resolver(io.netty.resolver.DefaultAddressResolverGroup.INSTANCE).followRedirect(false).responseTimeout(Duration.ofSeconds(20))))
        .codecs(c -> c.defaultCodecs().maxInMemorySize(8*1024*1024)).build();
    private long lastRequest;
    private final WebClient structureClient=WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create().resolver(io.netty.resolver.DefaultAddressResolverGroup.INSTANCE).followRedirect(false).responseTimeout(Duration.ofSeconds(20)))).codecs(c->c.defaultCodecs().maxInMemorySize(32*1024*1024)).build();
    private long lastSecRequest;
    private final java.util.Map<String,Long> cooldown=new java.util.concurrent.ConcurrentHashMap<>();
    public static void validate(URI uri) {
        boolean allowed = "api.gleif.org".equals(uri.getHost()) && gleifPath(uri) || "www.federalreserve.gov".equals(uri.getHost()) && "/feeds/press_all.xml".equals(uri.getPath())
            || "www.ecb.europa.eu".equals(uri.getHost()) && java.util.Set.of("/rss/press.html","/rss/statpress.html").contains(uri.getPath())
            || "data.sec.gov".equals(uri.getHost()) && uri.getPath().matches("/submissions/CIK[0-9]{10}\\.json")
            || "www.sec.gov".equals(uri.getHost()) && "/files/company_tickers.json".equals(uri.getPath())
            || "www.stats.gov.cn".equals(uri.getHost()) && java.util.Set.of("/sj/zxfb/rss.xml","/sj/sjjd/rss.xml").contains(uri.getPath())
            || "data-api.ecb.europa.eu".equals(uri.getHost()) && (((uri.getPath().matches("/service/data/EXR/D[.](USD|JPY|GBP|CNY)[.]EUR[.]SP00[.]A") || uri.getPath().matches("/service/data/FM/B[.]U2[.]EUR[.]4F[.]KR[.](DFR|MRR_FR|MLFR)[.]LEV")) && ecbQuery(uri)) || java.util.Set.of("/service/dataflow/ECB/EXR/latest","/service/dataflow/ECB/FM/latest").contains(uri.getPath()) && "references=all".equals(uri.getQuery()));
        if(!allowed || !"https".equals(uri.getScheme()) || uri.getPort()!=-1 || uri.getUserInfo()!=null || (uri.getQuery()!=null&&!"data-api.ecb.europa.eu".equals(uri.getHost())) || uri.getFragment()!=null) throw new IllegalArgumentException("请求地址不在官方白名单");
    }
    private static boolean ecbQuery(URI uri){
        if(uri.getQuery()==null)return false;var params=new java.util.HashMap<String,String>();
        for(String part:uri.getQuery().split("&")){String[] kv=part.split("=",2);if(kv.length!=2||params.put(kv[0],kv[1])!=null)return false;}
        if(!java.util.Set.of("startPeriod","endPeriod","includeHistory","updatedAfter","format").containsAll(params.keySet())||!params.containsKey("startPeriod")||!java.util.Set.of("csvdata","genericdata").contains(params.get("format")))return false;
        try{java.time.LocalDate.parse(params.get("startPeriod"));if(params.containsKey("endPeriod"))java.time.LocalDate.parse(params.get("endPeriod"));if(params.containsKey("updatedAfter"))java.time.Instant.parse(params.get("updatedAfter"));}catch(Exception e){return false;}
        return !params.containsKey("includeHistory")||"true".equals(params.get("includeHistory"));
    }
    private static boolean gleifPath(URI uri){
        if(uri.getPath().matches("/api/v1/lei-records/[A-Z0-9]{18}[0-9]{2}(/(direct|ultimate)-parent-(relationship|reporting-exception))?")&&uri.getRawQuery()==null)return true;
        if(!"/api/v1/lei-records".equals(uri.getPath())||uri.getRawQuery()==null)return false;
        return uri.getRawQuery().matches("page%5Bsize%5D=10&filter%5Bentity\\.legalName%5D=[A-Za-z0-9%+._-]{1,1000}");
    }
    public synchronized Result get(URI uri, String userAgent, Map<String,String> conditions) {
        validate(uri);
        if(System.currentTimeMillis()<cooldown.getOrDefault(uri.getHost(),0L)) return new Result(429,new byte[0],null,null);
        // One shared client serializes all source requests at <= 2 requests/sec.
        for(int attempt=0;attempt<3;attempt++) {
            try{if(guard!=null)guard.request(uri.getHost());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("采集中断",e);}
            boolean sec=uri.getHost().endsWith("sec.gov");
            try { Thread.sleep(Math.max(0,500-(System.currentTimeMillis()-Math.max(lastRequest,sec?lastSecRequest:0)))); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("采集中断",e); }
            lastRequest=System.currentTimeMillis();
            if(sec)lastSecRequest=lastRequest;
            long requestStarted=System.currentTimeMillis(),nano=System.nanoTime();
            Result result;
            int[] responseStatus={0};
            try { result = ("/service/dataflow/ECB/FM/latest".equals(uri.getPath())?structureClient:client).get().uri(uri).header("User-Agent",userAgent).headers(h -> conditions.forEach(h::set))
                .exchangeToMono(r -> {
                    responseStatus[0]=r.statusCode().value();
                    if(r.statusCode().value()==429){long until=retryAt(r.headers().asHttpHeaders().getFirst("Retry-After"));cooldown.put(uri.getHost(),until);if(guard!=null)guard.cooldown(uri.getHost(),until);}
                    return r.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).map(b -> new Result(r.statusCode().value(),b,r.headers().asHttpHeaders().getETag(),r.headers().asHttpHeaders().getFirst("Last-Modified")));
                })
                .block(Duration.ofSeconds(25));
            } catch(RuntimeException error) {observe(uri,requestStarted,(System.nanoTime()-nano)/1000000,responseStatus[0]==0?null:new Result(responseStatus[0],new byte[0],null,null),error);if(attempt==2)throw error;try{Thread.sleep(2000L*(1L<<attempt));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("采集中断",e);}continue;}
            observe(uri,requestStarted,(System.nanoTime()-nano)/1000000,result,null);
            if(result == null) throw new IllegalStateException("响应为空");
            if(result.status()<500 || attempt==2) return result;
            try { Thread.sleep(2000L*(1L<<attempt)); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("采集中断",e); }
        }
        throw new IllegalStateException("重试耗尽");
    }
    static long retryAt(String header) {
        long now=System.currentTimeMillis();
        try {return Math.addExact(now,Math.multiplyExact(Math.max(60,Long.parseLong(header)),1000));}
        catch(Exception e) {
            try {return Math.max(now+60000,java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli());}
            catch(Exception ignored){return now+300000;}
        }
    }
}
