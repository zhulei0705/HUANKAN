package cn.huankan.reliability;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Aspect @Component @Order(-100)
@ConditionalOnProperty(name="huankan.reliability-diagnostics-enabled",havingValue="true",matchIfMissing=true)
public class ExecutionDiagnostics extends OncePerRequestFilter {
    private final java.util.function.Supplier<MeterRegistry> metrics;
    @org.springframework.beans.factory.annotation.Autowired
    public ExecutionDiagnostics(org.springframework.beans.factory.ObjectProvider<MeterRegistry> metrics){this.metrics=metrics::getIfAvailable;}
    public ExecutionDiagnostics(MeterRegistry metrics){this.metrics=()->metrics;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        // Do not copy user-supplied correlation IDs, URI, query parameters or headers to logs.
        try(var context=new DiagnosticContext("HTTP入口")){response.setHeader("X-Huankan-Diagnostic-Id",DiagnosticContext.id());chain.doFilter(request,response);}
    }
    @Around("execution(public * cn.huankan..*Service.*(..)) || execution(public * cn.huankan.collector.*.collect(..)) || @annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object measure(ProceedingJoinPoint invocation)throws Throwable {
        String method=invocation.getSignature().getDeclaringType().getSimpleName()+"."+invocation.getSignature().getName();
        var signature=(org.aspectj.lang.reflect.MethodSignature)invocation.getSignature();
        boolean scheduled=signature.getMethod().isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class);
        long started=System.nanoTime();String outcome="success";
        try(var context=new DiagnosticContext(method)){
            try{return invocation.proceed();}catch(Throwable failure){outcome="error";throw failure;}
            finally{if(scheduled){var registry=metrics.get();if(registry!=null)registry.timer("huankan.scheduler.duration","task",method,"outcome",outcome).record(System.nanoTime()-started,TimeUnit.NANOSECONDS);LoggerFactory.getLogger(getClass()).info("调度阶段：关联={}，任务={}，结果={}，耗时={}毫秒，指标注册器可用={}",DiagnosticContext.id(),method,outcome,(System.nanoTime()-started)/1_000_000,registry!=null);}}
        }
    }
}
