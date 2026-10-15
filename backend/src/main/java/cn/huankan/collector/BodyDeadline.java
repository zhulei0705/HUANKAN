package cn.huankan.collector;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.*;
/** HttpClient's ofInputStream response completes at headers; enforce a body deadline too. */
final class BodyDeadline {
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"HTTP响应读取截止时间");t.setDaemon(true);return t;});
    static Ticket arm(InputStream stream,Duration duration){return new Ticket(stream,duration);}
    static final class Ticket implements AutoCloseable {
        private final java.util.concurrent.atomic.AtomicBoolean expired=new java.util.concurrent.atomic.AtomicBoolean();private final ScheduledFuture<?> timer;
        Ticket(InputStream stream,Duration duration){timer=TIMER.schedule(()->{expired.set(true);try{stream.close();}catch(Exception ignored){}},duration.toMillis(),TimeUnit.MILLISECONDS);}
        void verify()throws java.net.SocketTimeoutException{if(expired.get())throw new java.net.SocketTimeoutException("响应正文读取超过截止时间");}
        public void close(){timer.cancel(false);}
    }
}
