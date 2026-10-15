package cn.huankan.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import java.io.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/** Failure evidence only: never obtains an extra connection or retries a failed business action. */
public final class JdbcDiagnostics {
    private JdbcDiagnostics() {}
    public static Map<String,Object> pool(JdbcTemplate db){
        var record=new LinkedHashMap<String,Object>();
        var source=db.getDataSource();
        if(source instanceof ObservedDataSource observed){record.put("holders",observed.holders());source=observed.hikari();}
        if(source instanceof HikariDataSource hikari){
            record.put("pool",hikari.getPoolName());record.put("maximum",hikari.getMaximumPoolSize());record.put("minimumIdle",hikari.getMinimumIdle());record.put("connectionTimeoutMs",hikari.getConnectionTimeout());record.put("validationTimeoutMs",hikari.getValidationTimeout());record.put("idleTimeoutMs",hikari.getIdleTimeout());record.put("maxLifetimeMs",hikari.getMaxLifetime());record.put("keepaliveTimeMs",hikari.getKeepaliveTime());
            record.put("leakDetectionThresholdMs",hikari.getLeakDetectionThreshold());
            var mx=hikari.getHikariPoolMXBean();if(mx!=null){record.put("active",mx.getActiveConnections());record.put("idle",mx.getIdleConnections());record.put("pending",mx.getThreadsAwaitingConnection());record.put("total",mx.getTotalConnections());}
        }else record.put("pool","当前数据源未直接提供Hikari指标");
        return record;
    }
    public static String redact(String value){String safe=value;for(String key:List.of("HUANKAN_DB_PASSWORD","HUANKAN_TEST_DB_PASSWORD","HUANKAN_ADMIN_TOKEN","FRED_API_KEY","AI_API_KEY")){String secret=System.getenv(key);if(secret!=null&&!secret.isBlank())safe=safe.replace(secret,"[已脱敏]");}return safe.replaceAll("(?i)(password|pwd|token|secret|authorization)\\s*[=:]\\s*[^\\s,;&]+","$1=[已脱敏]").replaceAll("(jdbc:postgresql://[^\\s?]+)\\?[^\\s]+","$1?[连接参数已脱敏]");}
    public static Map<String,Object> evidence(String task,String state,JdbcTemplate db,Throwable failure){
        var record=new LinkedHashMap<String,Object>();record.put("at",Instant.now().toString());record.put("diagnostic_id",DiagnosticContext.id());record.put("task",task);record.put("state",state);record.put("pool",pool(db));
        var chain=new ArrayList<Map<String,Object>>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        var pending=new ArrayDeque<Throwable>();pending.add(failure);
        while(!pending.isEmpty()){
            Throwable cause=pending.removeFirst();if(!seen.add(cause))continue;
            var row=new LinkedHashMap<String,Object>();row.put("type",cause.getClass().getName());row.put("message",redact(Objects.toString(cause.getMessage(),"")));
            if(cause instanceof SQLException sql){row.put("sqlState",sql.getSQLState());row.put("errorCode",sql.getErrorCode());if(sql.getNextException()!=null)pending.add(sql.getNextException());}chain.add(row);
            if(cause.getCause()!=null)pending.add(cause.getCause());for(Throwable suppressed:cause.getSuppressed())pending.add(suppressed);
        }
        record.put("exceptions",chain);StringWriter text=new StringWriter();failure.printStackTrace(new PrintWriter(text));record.put("exceptionChain",redact(text.toString()));return record;
    }
    public static void failure(String task,String state,JdbcTemplate db,Throwable failure){
        try{
            var safe=evidence(task,state,db,failure);safe.remove("exceptionChain");
            // Driver exception messages may contain full SQL and literal values. Retain the
            // complete type/state/code chain in ordinary logs; controlled test evidence is separate.
            ((java.util.List<?>)safe.get("exceptions")).forEach(row->((java.util.Map<?,?>)row).remove("message"));
            LoggerFactory.getLogger(JdbcDiagnostics.class).error("任务/数据库/调度失败诊断：{}",new ObjectMapper().writeValueAsString(safe));
        }
        catch(Exception serialization){LoggerFactory.getLogger(JdbcDiagnostics.class).error("数据库/调度诊断序列化失败：{}，原异常类型：{}",task,failure.getClass().getName());}
    }
}
