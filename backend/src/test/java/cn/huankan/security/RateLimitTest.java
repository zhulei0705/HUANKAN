package cn.huankan.security;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitTest {
    @Test void limitsRequestsAndAllowsNewWindow() throws Exception {
        var time=new AtomicLong(0);var filter=new RequestRateLimitFilter(time::get);
        for(int i=0;i<120;i++){var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/stats"),response,(r,s)->{});assertEquals(200,response.getStatus());}
        var blocked=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/stats"),blocked,(r,s)->fail("不应到达业务处理"));assertEquals(429,blocked.getStatus());
        time.set(60000);var resumed=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("POST","/api/stats"),resumed,(r,s)->{});assertEquals(200,resumed.getStatus());
    }
}
