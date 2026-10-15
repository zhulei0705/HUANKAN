package cn.huankan.collector;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.nio.charset.StandardCharsets;import java.math.BigDecimal;
class BlsSourceAdapterTest {
 static byte[] response(String rows){return ("{\"status\":\"REQUEST_SUCCEEDED\",\"Results\":{\"series\":[{\"seriesID\":\"LNS14000000\",\"data\":["+rows+"]}]}}").getBytes(StandardCharsets.UTF_8);}
 static String row(String period,String value,String notes){return "{ \"year\": \"2026\", \"period\": \""+period+"\", \"value\": \""+value+"\", \"footnotes\": "+notes+" }";}
 @Test void numericValueMonthAndLiteralArePreserved(){var bytes=response(row("M09","4.2","[{\"code\":\"P\",\"text\":\"preliminary\"}]"));var o=BlsSourceAdapter.observations(bytes).getFirst();assertEquals(new BigDecimal("4.2"),o.value());assertEquals("2026-09-01",o.date().toString());assertEquals("PRELIMINARY",o.releaseStatus());assertTrue(new String(bytes,StandardCharsets.UTF_8).contains(o.literal()));assertNull(new BlsSourceAdapter().parse(bytes).getFirst().publishedAt());}
 @Test void businessFailureIsNotSuccessfulEvenWithJson(){assertThrows(IllegalArgumentException.class,()->BlsSourceAdapter.observations("{\"status\":\"REQUEST_NOT_PROCESSED\",\"Results\":{\"series\":[]}}".getBytes(StandardCharsets.UTF_8)));}
 @Test void annualAverageIsNotInventedMonth(){var list=BlsSourceAdapter.observations(response(row("M13","4.0","[{}]")+","+row("M01","4.2","[{}]")));assertEquals(1,list.size());}
 @Test void duplicatePeriodsAndUnknownMonthsAreRejected(){assertThrows(IllegalArgumentException.class,()->BlsSourceAdapter.observations(response(row("M09","4.2","[{}]")+","+row("M09","4.3","[{}]"))));assertThrows(IllegalArgumentException.class,()->BlsSourceAdapter.observations(response(row("M99","4.2","[{}]"))));}
 @Test void missingObservationStaysMissingAndRevisionFootnoteIsSeparate(){var o=BlsSourceAdapter.observations(response(row("M10","-","[{\"text\":\"Revised population controls\"}]"))).getFirst();assertNull(o.value());assertEquals("-",o.rawValue());assertEquals("REVISED_NOTE",o.releaseStatus());}
 @Test void duplicateJsonStatusCannotHideBusinessFailure(){assertThrows(IllegalArgumentException.class,()->BlsSourceAdapter.observations("{\"status\":\"REQUEST_NOT_PROCESSED\",\"status\":\"REQUEST_SUCCEEDED\"}".getBytes(StandardCharsets.UTF_8)));}
}
