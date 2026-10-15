package cn.huankan.reliability;

import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name="huankan.reliability-diagnostics-enabled",havingValue="true",matchIfMissing=true)
public class ReliabilityController {
    private final JdbcTemplate db;
    public ReliabilityController(JdbcTemplate db){this.db=db;}
    /** Existing ADMIN authorization applies; no secrets, SQL text or business writes. */
    @PostMapping("/api/reliability/status") public Map<String,Object> status(){
        return Map.of("at",Instant.now().toString(),"timezone",ZoneId.systemDefault().toString(),"jvm_uptime_ms",java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(),"pool",JdbcDiagnostics.pool(db),"diagnostic_id",DiagnosticContext.id());
    }
}
