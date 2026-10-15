import java.nio.file.*;
import cn.huankan.collector.*;
try {SdmxCsv.validateStructure(Files.readAllBytes(Path.of("runtime/s17-flow.xml")));System.out.println("SDMX_STRUCTURE_OK");}catch(Exception e){e.printStackTrace();}
try {System.setProperty("javax.net.ssl.trustStoreType","Windows-ROOT");System.setProperty("javax.net.ssl.trustStore","NONE");var c=java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();var r=c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://www.stats.gov.cn/sj/zxfb/rss.xml")).timeout(java.time.Duration.ofSeconds(25)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());System.out.println("NBS_WINDOWS_ROOT_STATUS="+r.statusCode()+" bytes="+r.body().length+" items="+NbsCollector.segments(r.body()).size());}catch(Exception e){e.printStackTrace();}
/exit
