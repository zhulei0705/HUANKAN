package cn.huankan.collector;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class BodyDeadlineTest {
    @Test void timedOutBodyCannotBeRecordedAsComplete()throws Exception{
        var closed=new java.util.concurrent.CountDownLatch(1);var input=new java.io.ByteArrayInputStream(new byte[0]){public void close(){closed.countDown();}};
        try(var deadline=BodyDeadline.arm(input,Duration.ofMillis(10))){assertTrue(closed.await(2,java.util.concurrent.TimeUnit.SECONDS));assertThrows(java.net.SocketTimeoutException.class,deadline::verify);}
    }
}
