package cn.huankan.collector;
import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class CollectorFailureTest {
 @ParameterizedTest @ValueSource(ints={403,429,503}) void httpFailureNeverCreatesEvidenceOrSuccess(int status){
  var db=mock(JdbcTemplate.class);var http=mock(OfficialHttpClient.class);var evidence=mock(EvidenceService.class);
  when(db.queryForObject(anyString(),eq(Boolean.class),eq("FED"))).thenReturn(true);
  when(http.get(any(URI.class),anyString(),anyMap())).thenReturn(new OfficialHttpClient.Result(status,new byte[0],null,null));
  new CollectorService(db,http,evidence,false,new SecConfiguration("","","")).collect("FED");
  verifyNoInteractions(evidence);
  verify(db).update(contains("status='FAILED'"),eq(status),eq(0),eq(status>=500?"HTTP_5XX":"HTTP_"+status),eq("HTTP_"+status),anyLong(),any());
  verify(db,never()).update(contains("status='SUCCESS'"),any(),any(),any(),any());
 }
 @Test void disconnectedSourceNeverGeneratesBusinessRecords(){
  var db=mock(JdbcTemplate.class);var http=mock(OfficialHttpClient.class);var evidence=mock(EvidenceService.class);
  when(db.queryForObject(anyString(),eq(Boolean.class),eq("FED"))).thenReturn(true);
  when(http.get(any(URI.class),anyString(),anyMap())).thenThrow(new IllegalStateException("isolated network failure"));
  new CollectorService(db,http,evidence,false,new SecConfiguration("","","")).collect("FED");verifyNoInteractions(evidence);
 }
 @Test void oneSourceConfigurationFailureDoesNotStopOtherSources(){
  var db=mock(JdbcTemplate.class);when(db.queryForList(anyString())).thenReturn(java.util.List.of(Map.of("id","FRED"),Map.of("id","FED")));
  var service=spy(new CollectorService(db,mock(OfficialHttpClient.class),mock(EvidenceService.class),true,new SecConfiguration("","","")));
  doThrow(new IllegalStateException("ISOLATED missing key")).when(service).collect("FRED");doNothing().when(service).collect("FED");service.scheduled();verify(service).collect("FED");
 }
 @Test void invalidContentIsAParsingAnomalyInsteadOfRequestOrFinancialFact(){
  var db=mock(JdbcTemplate.class);var http=mock(OfficialHttpClient.class);var evidence=mock(EvidenceService.class);when(db.queryForObject(anyString(),eq(Boolean.class),eq("FED"))).thenReturn(true);when(http.get(any(),anyString(),anyMap())).thenReturn(new OfficialHttpClient.Result(200,"ISOLATED INVALID XML".getBytes(),null,null));new CollectorService(db,http,evidence,false,new SecConfiguration("","","")).collect("FED");verifyNoInteractions(evidence);verify(db).update(contains("anomaly_level"),eq("PARSE"),any());
 }
}
