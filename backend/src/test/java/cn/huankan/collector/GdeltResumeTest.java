package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GdeltResumeTest {
 @TempDir Path directory;
 @SuppressWarnings("unchecked") @Test void interruptedFileResumesAtActualPersistedOffset()throws Exception{
  var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);var downloader=new GdeltDownloader(db);var client=mock(HttpClient.class);
  org.springframework.test.util.ReflectionTestUtils.setField(downloader,"client",client);org.springframework.test.util.ReflectionTestUtils.setField(downloader,"directory",directory);
  var first=(HttpResponse<InputStream>)mock(HttpResponse.class);when(first.statusCode()).thenReturn(200);when(first.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
  when(first.body()).thenReturn(new InputStream(){int pos;public int read()throws IOException{if(pos==3)throw new IOException("ISOLATED TRANSPORT INTERRUPTION");return "abcdef".charAt(pos++);}public int read(byte[] b,int off,int len)throws IOException{if(pos==3)throw new IOException("ISOLATED TRANSPORT INTERRUPTION");int n=3-pos;for(int i=0;i<n;i++)b[off+i]=(byte)"abcdef".charAt(pos++);return n;}});
  var second=(HttpResponse<InputStream>)mock(HttpResponse.class);when(second.statusCode()).thenReturn(206);when(second.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Range",List.of("bytes 3-5/6")),(a,b)->true));when(second.body()).thenReturn(new ByteArrayInputStream("def".getBytes()));
  when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(first,second);
  var url=URI.create("https://data.gdeltproject.org/gdeltv2/20990101000000.gkg.csv.zip");assertThrows(IOException.class,()->downloader.get(url,6,"20990101000000"));assertEquals(3,Files.size(directory.resolve("20990101000000.part")));
  assertArrayEquals("abcdef".getBytes(),downloader.get(url,6,"20990101000000"));
  assertArrayEquals("abcdef".getBytes(),downloader.get(url,6,"20990101000000")); // completed local bytes reused after crash
  var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(client,times(2)).send(request.capture(),any(HttpResponse.BodyHandler.class));assertEquals("bytes=3-",request.getAllValues().get(1).headers().firstValue("Range").orElseThrow());
 }
}
