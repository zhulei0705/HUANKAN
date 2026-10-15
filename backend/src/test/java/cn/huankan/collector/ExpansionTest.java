package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.zip.*;
class ExpansionTest {
 @Test void atomUsesPublishedAndOfficialHost(){var xml="<feed xmlns='http://www.w3.org/2005/Atom'><entry><id>x</id><title>ISOLATED TEST</title><link href='https://www.ecb.europa.eu/press/test'/><published>2026-01-01T12:00:00+02:00</published></entry></feed>";var items=new RssAdapter("www.ecb.europa.eu").parse(xml.getBytes(StandardCharsets.UTF_8));assertEquals("2026-01-01T10:00:00Z",items.getFirst().publishedAt().toString());assertThrows(IllegalArgumentException.class,()->new FedAdapter().parse(xml.getBytes(StandardCharsets.UTF_8)));}
 byte[] zip(String name,String row)throws Exception{var b=new ByteArrayOutputStream();try(var z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry(name));z.write(row.getBytes(StandardCharsets.UTF_8));z.closeEntry();}return b.toByteArray();}
 @Test void gdeltParsesMetadataWithoutInventingPublication()throws Exception{String[] fields=new String[27];java.util.Arrays.fill(fields,"");fields[0]="20260101120000-1";fields[1]="20260101120000";fields[3]="isolated.test";fields[4]="https://isolated.test/article";fields[7]="ECON_INTEREST_RATES;";fields[9]="1#United States#US#US#0#0#US";var result=GdeltParser.parse(zip("20260101120000.gkg.csv",String.join("\t",fields)+"\n"),"20260101120000");assertEquals(1,result.total());assertEquals("US",result.leads().getFirst().countries());assertEquals("https://isolated.test/article",result.leads().getFirst().documentUrl());}
 @Test void gdeltRejectsZipTraversalAndMalformedColumns()throws Exception{assertThrows(IOException.class,()->GdeltParser.parse(zip("../escape","test"),"20260101120000"));assertThrows(IOException.class,()->GdeltParser.parse(zip("20260101120000.gkg.csv","bad\tcolumns"),"20260101120000"));}
 @Test void gdeltDownloadWhitelistRejectsExternalAndHttp(){assertThrows(IllegalArgumentException.class,()->GdeltDownloader.validate(java.net.URI.create("https://evil.test/gdeltv2/lastupdate.txt")));assertThrows(IllegalArgumentException.class,()->GdeltDownloader.validate(java.net.URI.create("http://data.gdeltproject.org/gdeltv2/lastupdate.txt")));}
}
