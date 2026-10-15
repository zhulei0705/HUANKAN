package cn.huankan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestRateLimitFilter extends OncePerRequestFilter {
    private final LongSupplier clock;
    private long window=-1; private int count;
    public RequestRateLimitFilter() {this(System::currentTimeMillis);}
    RequestRateLimitFilter(LongSupplier clock) {this.clock=clock;}
    private synchronized boolean allowed() {
        long current=clock.getAsLong()/60000;
        if(current!=window){window=current;count=0;}
        return ++count<=120;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        if(request.getRequestURI().startsWith("/api/")&&!allowed()) {
            response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"message\":\"请求过于频繁，请稍后重试\"}");return;
        }
        chain.doFilter(request,response);
    }
}
