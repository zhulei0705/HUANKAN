package cn.huankan.collector;

import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Session lock fences a stalled owner even after its observable lease expires. */
@Component
public class CollectorGuard {
    private final JdbcTemplate db;
    public CollectorGuard(JdbcTemplate db){this.db=db;}
    public AutoCloseable acquire(String source) throws Exception {
        Connection connection=Objects.requireNonNull(db.getDataSource()).getConnection();
        try(var timeout=connection.createStatement()){timeout.execute("SET idle_session_timeout='3min'");}catch(Exception e){connection.close();throw e;}
        try(var statement=connection.prepareStatement("SELECT pg_try_advisory_lock(742020,hashtext(?))")){
            statement.setString(1,source);var result=statement.executeQuery();result.next();if(!result.getBoolean(1)){try(var reset=connection.createStatement()){reset.execute("SET idle_session_timeout=0");}connection.close();org.slf4j.LoggerFactory.getLogger(getClass()).info("来源{}互斥锁合法跳过：SKIPPED_LOCK_BUSY，未创建采集任务",source);return null;}
        }catch(Exception e){connection.close();throw e;}
        UUID owner=UUID.randomUUID();
        try{
            db.update("INSERT INTO collector_lease(source_id,owner,expires_at) VALUES(?,?,now()+interval '2 minutes') ON CONFLICT(source_id) DO UPDATE SET owner=excluded.owner,acquired_at=now(),heartbeat_at=now(),expires_at=excluded.expires_at",source,owner);
            db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now(),error_code='OWNER_LOST',error_detail='前一采集进程中断，已恢复调度' WHERE source_id=? AND status='RUNNING'",source);
        }catch(Exception e){try(var unlock=connection.prepareStatement("SELECT pg_advisory_unlock(742020,hashtext(?))")){unlock.setString(1,source);unlock.execute();}finally{connection.close();}throw e;}
        var heartbeats=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"采集租约心跳");t.setDaemon(true);return t;});
        String parentDiagnostic=cn.huankan.reliability.DiagnosticContext.id();
        heartbeats.scheduleWithFixedDelay(()->{try(var context=cn.huankan.reliability.DiagnosticContext.continueInternal("采集租约心跳",parentDiagnostic)){try{try(var alive=connection.createStatement()){alive.execute("SELECT 1");}db.update("UPDATE collector_lease SET heartbeat_at=now(),expires_at=now()+interval '2 minutes' WHERE source_id=? AND owner=?",source,owner);db.update("UPDATE ingestion_job SET heartbeat_at=now() WHERE source_id=? AND status='RUNNING'",source);}catch(Exception failure){cn.huankan.reliability.JdbcDiagnostics.failure("采集租约心跳/"+source,"LEASE_HEARTBEAT_FAILED",db,failure);/* Expired/disconnected owner remains fenced at evidence write time. */}}},10,10,TimeUnit.SECONDS);
        return ()->{heartbeats.shutdownNow();try{db.update("DELETE FROM collector_lease WHERE source_id=? AND owner=?",source,owner);}finally{try(var statement=connection.prepareStatement("SELECT pg_advisory_unlock(742020,hashtext(?))")){statement.setString(1,source);statement.execute();try(var reset=connection.createStatement()){reset.execute("SET idle_session_timeout=0");}}finally{connection.close();}}};
    }
    public void request(String domain) throws InterruptedException {
        String key=domain.endsWith("sec.gov")?"sec.gov":domain;
        // Reserved slots persist across instances and restarts; 2/sec, 120/min per domain.
        var row=db.queryForMap("INSERT INTO domain_budget(domain,next_at,requests) VALUES(?,now()+interval '500 milliseconds',1) ON CONFLICT(domain) DO UPDATE SET next_at=greatest(now(),domain_budget.next_at,COALESCE(domain_budget.cooldown_until,now()),CASE WHEN domain_budget.requests>=120 AND domain_budget.window_started>now()-interval '1 minute' THEN domain_budget.window_started+interval '1 minute' ELSE now() END)+interval '500 milliseconds',requests=CASE WHEN domain_budget.window_started<now()-interval '1 minute' THEN 1 ELSE domain_budget.requests+1 END,window_started=CASE WHEN domain_budget.window_started<now()-interval '1 minute' THEN now() ELSE domain_budget.window_started END RETURNING greatest(0,extract(epoch FROM next_at-now())*1000-500) wait_ms",key);
        long wait=((Number)row.get("wait_ms")).longValue();if(wait>60000)throw new IllegalStateException("请求预算冷却，未发送HTTP请求");Thread.sleep(wait);
    }
    public void cooldown(String domain,long until){db.update("UPDATE domain_budget SET cooldown_until=? WHERE domain=?",java.sql.Timestamp.from(Instant.ofEpochMilli(until)),domain.endsWith("sec.gov")?"sec.gov":domain);}
    public static String failure(Throwable e){
        for(Throwable cause=e;cause!=null;cause=cause.getCause()){
            if(cause instanceof java.sql.SQLException sql){String state=Objects.toString(sql.getSQLState(),"");
                if(state.startsWith("08")||state.equals("57P01")||state.equals("57P02")||state.equals("57P03"))return "DATABASE_CONNECTION_FAILURE";
                if(state.equals("57014"))return "DATABASE_QUERY_TIMEOUT";
                if(state.startsWith("40"))return "DATABASE_TRANSACTION_FAILURE";
                if(cause instanceof java.sql.SQLTransientConnectionException)return "DATABASE_CONNECTION_TIMEOUT";
                return "DATABASE_SQL_FAILURE";
            }
        }
        StringBuilder chain=new StringBuilder();for(Throwable c=e;c!=null;c=c.getCause())chain.append(c.getClass().getSimpleName()).append(':').append(c.getMessage()).append(' ');
        String s=chain.toString().toLowerCase(Locale.ROOT);
        if(s.contains("unknownhost")||s.contains("resolve")||s.contains("dns"))return "DNS_FAILURE";
        if(s.contains("ssl")||s.contains("handshake"))return "TLS_FAILURE";
        if(s.contains("timeout")||s.contains("timed out"))return "REQUEST_TIMEOUT";
        if(s.contains("connectexception")||s.contains("connection refused"))return "TCP_FAILURE";
        for(String status:List.of("403","404","429"))if(s.contains("http_"+status))return "HTTP_"+status;
        if(s.matches("(?s).*http_5[0-9]{2}.*"))return "HTTP_5XX";
        if(s.contains("saxparse")||s.contains("jsonparse")||s.contains("jsonprocessing"))return "PARSE_FAILURE";
        if(s.contains("hash")||s.contains("sha")||s.contains("md5")||s.contains("校验"))return "INTEGRITY_FAILURE";
        return e instanceof IllegalArgumentException?"PARSE_FAILURE":"STORAGE_OR_TRANSPORT_FAILURE";
    }
}
