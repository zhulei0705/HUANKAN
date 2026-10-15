package cn.huankan.collector;

import cn.huankan.evidence.EvidenceService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptersTest {
    private byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
    // Isolated artificial fixture; never inserted into the business database.
    @Test void parsesPublicationTimeWithoutReplacingItWithCollectionTime() {
        var items=new FedAdapter().parse(bytes("<rss><channel><item><guid>fixture-only</guid><title>测试标题</title><link>https://www.federalreserve.gov/newsevents/test.htm</link><pubDate>Wed, 07 Oct 2026 14:00:00 GMT</pubDate></item></channel></rss>"));
        assertEquals("2026-10-07T14:00:00Z",items.getFirst().publishedAt().toString());
    }
    @Test void rejectsXmlExternalEntities() {assertThrows(IllegalArgumentException.class,() -> new FedAdapter().parse(bytes("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///C:/Windows/win.ini'>]><rss><channel>&x;</channel></rss>")));}
    @Test void rejectsForgedSourceLinks() {assertThrows(IllegalArgumentException.class,() -> new FedAdapter().parse(bytes("<rss><item><title>fixture</title><link>https://www.federalreserve.gov.attacker.invalid/news</link></item></rss>")));}
    @Test void rejectsPrivateAndUnapprovedUrls() {
        for(String url:new String[]{"http://127.0.0.1/","https://localhost/","https://data.sec.gov@evil.invalid/submissions/CIK0000000001.json","https://www.federalreserve.gov/feeds/press_all.xml?redirect=x","https://data.sec.gov:443/submissions/CIK0000000001.json"}) assertThrows(IllegalArgumentException.class,() -> OfficialHttpClient.validate(URI.create(url)));
        assertDoesNotThrow(() -> OfficialHttpClient.validate(URI.create("https://data.sec.gov/submissions/CIK0000320193.json")));
    }
    @Test void preservesMissingPublicationTime() {
        var item=new FedAdapter().parse(bytes("<rss><item><title>fixture</title><link>https://www.federalreserve.gov/news</link></item></rss>")).getFirst();
        assertNull(item.publishedAt());
    }
    @Test void rejectsChangedSecColumnStructure() {assertThrows(IllegalArgumentException.class,() -> new SecAdapter().parse(bytes("{\"cik\":1,\"filings\":{\"recent\":{\"accessionNumber\":[\"0000000001-26-000001\"],\"form\":[]}}}")));}
    @Test void hashUsesActualBytes() {assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",EvidenceService.hash(bytes("abc")));}
    @Test void itemFingerprintDoesNotDependOnOtherFeedItems() {
        String first="<item><guid>fixture</guid><title>标题</title><link>https://www.federalreserve.gov/fixture</link></item>";
        String second="<item><guid>another</guid><title>其他标题</title><link>https://www.federalreserve.gov/another</link></item>";
        var item=new FedAdapter().parse(bytes("<rss>"+first+"</rss>")).getFirst();
        var withAnother=new FedAdapter().parse(bytes("<rss>"+first+second+"</rss>")).getFirst();
        assertEquals(EvidenceService.fingerprint(item),EvidenceService.fingerprint(withAnother));
        assertNotEquals(EvidenceService.fingerprint(item),EvidenceService.fingerprint(new SourceAdapter.Item("fixture","修订标题",item.url(),null,item.category())));
    }
}
