import java.net.*;
import java.net.http.*;
import java.time.*;
import cn.huankan.collector.FedAdapter;
try {
 var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
 var request=HttpRequest.newBuilder(URI.create("https://www.federalreserve.gov/feeds/press_all.xml")).timeout(Duration.ofSeconds(25)).header("User-Agent","HUANKAN/0.1 (local RSS reader)").build();
 var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
 try(var body=response.body()) {
  var bytes=body.readNBytes(4*1024*1024+1);
  if(response.statusCode()!=200||bytes.length>4*1024*1024)throw new IllegalStateException("HTTP或响应大小校验失败");
  var items=new FedAdapter().parse(bytes);
  System.out.println("CHECKED_AT="+Instant.now()+" HTTP="+response.statusCode()+" RAW_BYTES="+bytes.length+" PARSED_ITEMS="+items.size());
  System.out.println("MISSING_PUBLICATION_TIME="+items.stream().filter(i->i.publishedAt()==null).count());
  System.out.println("RESULT=SUCCESS CONNECTIVITY_AND_PARSER_ONLY; NO_DATABASE_WRITE; NO_END_TO_END_ACCEPTANCE");
 }
} catch(Exception e) { System.out.println("RESULT=FAILED "+e.getClass().getSimpleName()+" "+e.getMessage()); }
/exit
