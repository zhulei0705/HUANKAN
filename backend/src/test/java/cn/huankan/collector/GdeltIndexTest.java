package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class GdeltIndexTest {
    String line(String kind){return "123 "+"a".repeat(32)+" http://data.gdeltproject.org/gdeltv2/20261009060000."+kind+".zip";}
    @Test void realIndexTypesAndFutureGuard(){var entries=GdeltIndex.latest((line("export.CSV")+"\n"+line("mentions.CSV")+"\n"+line("gkg.csv")).getBytes(StandardCharsets.US_ASCII));assertEquals(3,entries.size());var gkg=entries.get(2);assertFalse(gkg.downloadable(Instant.parse("2026-10-09T05:57:00Z")));assertTrue(gkg.downloadable(Instant.parse("2026-10-09T06:00:00Z")));assertEquals("https://data.gdeltproject.org/gdeltv2/20261009060000.gkg.csv.zip",gkg.url());}
    @Test void rejectsForgedAndIncompleteIndex(){assertThrows(IllegalArgumentException.class,()->GdeltIndex.line(line("gkg.csv").replace("data.gdeltproject.org","attacker.invalid")));assertThrows(IllegalArgumentException.class,()->GdeltIndex.latest(line("gkg.csv").getBytes(StandardCharsets.US_ASCII)));assertThrows(IllegalArgumentException.class,()->GdeltIndex.line(line("gkg.csv").replace("20261009060000","20261309060000")));}
    @Test void downtimeSlotsAreUtcAndBounded(){assertEquals(java.util.List.of("20261009051500","20261009053000","20261009054500"),GdeltIndex.slots("20261009050000","20261009060000",10));assertEquals(Instant.parse("2026-10-09T06:00:00Z"),ZonedDateTime.parse("2026-10-09T14:00:00+08:00").toInstant());assertEquals(2,GdeltIndex.slots("20261009000000","20261010000000",2).size());}
    @Test void distinguishesPublishedLagFromHistoricalLoss(){var e=new java.io.IOException("GDELT_HTTP_404");assertEquals("PUBLISHED_NOT_AVAILABLE",GdeltIndex.failure(e,"20261009060000",Instant.parse("2026-10-09T06:01:00Z")));assertEquals("MISSING_HISTORICAL_BATCH",GdeltIndex.failure(e,"20261009060000",Instant.parse("2026-10-09T08:00:00Z")));assertEquals("DNS_FAILURE",CollectorGuard.failure(new IllegalStateException("Failed to resolve host")));assertEquals("TLS_FAILURE",CollectorGuard.failure(new IllegalStateException("handshake timed out")));}
}
