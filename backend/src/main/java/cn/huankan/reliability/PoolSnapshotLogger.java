package cn.huankan.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name="huankan.reliability-diagnostics-enabled",havingValue="true",matchIfMissing=true)
public class PoolSnapshotLogger {
    private final JdbcTemplate db;
    public PoolSnapshotLogger(JdbcTemplate db,io.micrometer.core.instrument.MeterRegistry metrics){
        this.db=db;
        var source=db.getDataSource();
        var pool=source instanceof ObservedDataSource observed?observed.hikari():source instanceof com.zaxxer.hikari.HikariDataSource hikari?hikari:null;
        if(pool!=null){
            metrics.gauge("huankan.jdbc.maximum",pool,p->p.getMaximumPoolSize());
            io.micrometer.core.instrument.Gauge.builder("huankan.jdbc.active",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getActiveConnections()).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("huankan.jdbc.idle",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getIdleConnections()).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("huankan.jdbc.pending",pool,p->p.getHikariPoolMXBean()==null?Double.NaN:p.getHikariPoolMXBean().getThreadsAwaitingConnection()).register(metrics);
        }
    }
    @Scheduled(fixedDelay=30000,initialDelay=30000) public void sample(){
        try{org.slf4j.LoggerFactory.getLogger(getClass()).info("连接池实际采样：{}",new ObjectMapper().writeValueAsString(Map.of("at",Instant.now().toString(),"pool",JdbcDiagnostics.pool(db))));}
        catch(Exception failure){org.slf4j.LoggerFactory.getLogger(getClass()).warn("连接池采样失败，当前指标未知：{}",JdbcDiagnostics.redact(failure.toString()));}
    }
}
