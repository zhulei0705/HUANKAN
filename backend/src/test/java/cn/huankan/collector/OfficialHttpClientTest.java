package cn.huankan.collector;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;
class OfficialHttpClientTest {
 private OfficialHttpClient client(AtomicInteger calls,HttpStatus status,String retry){
  var http=new OfficialHttpClient();
  var web=WebClient.builder().exchangeFunction(request->{calls.incrementAndGet();var response=ClientResponse.create(status);if(retry!=null)response.header("Retry-After",retry);return Mono.just(response.body("isolated transport fixture").build());}).build();
  ReflectionTestUtils.setField(http,"client",web);return http;
 }
 @Test void forbiddenResponseIsNotRetriedOrReportedAsSuccess(){var calls=new AtomicInteger();var http=client(calls,HttpStatus.FORBIDDEN,null);assertEquals(403,http.get(URI.create("https://data.sec.gov/submissions/CIK0000000001.json"),"isolated-test-only",Map.of()).status());assertEquals(1,calls.get());}
 @Test void rateLimitedHostRespectsCooldownWithoutSendingSecondRequest(){var calls=new AtomicInteger();var http=client(calls,HttpStatus.TOO_MANY_REQUESTS,"60");var uri=URI.create("https://www.sec.gov/files/company_tickers.json");assertEquals(429,http.get(uri,"isolated-test-only",Map.of()).status());assertEquals(429,http.get(uri,"isolated-test-only",Map.of()).status());assertEquals(1,calls.get());}
 @Test void serverFailureHasThreeAttemptUpperBound(){var calls=new AtomicInteger();var http=client(calls,HttpStatus.SERVICE_UNAVAILABLE,null);assertEquals(503,http.get(URI.create("https://data.sec.gov/submissions/CIK0000000001.json"),"isolated-test-only",Map.of()).status());assertEquals(3,calls.get());}
 @Test void eachRealAttemptIsLoggedButCooldownIsNot(){var calls=new AtomicInteger();var http=client(calls,HttpStatus.TOO_MANY_REQUESTS,"60");var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);ReflectionTestUtils.setField(http,"observations",db);var uri=URI.create("https://www.sec.gov/files/company_tickers.json");http.get(uri,"isolated",Map.of());http.get(uri,"isolated",Map.of());org.mockito.Mockito.verify(db,org.mockito.Mockito.times(1)).update(org.mockito.ArgumentMatchers.contains("http_request_observation"),org.mockito.ArgumentMatchers.eq("SEC"),org.mockito.ArgumentMatchers.eq(uri.toString()),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(429),org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull());}
}
