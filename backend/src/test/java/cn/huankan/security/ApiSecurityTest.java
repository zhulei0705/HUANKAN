package cn.huankan.security;

import cn.huankan.event.ApiController;
import cn.huankan.event.QueryService;
import cn.huankan.collector.CollectorService;
import cn.huankan.stream.StreamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value=ApiController.class,properties="huankan.token=isolated-test-token-32-characters-minimum")
@Import(SecurityConfig.class)
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean QueryService query;
    @MockitoBean CollectorService collector;
    @MockitoBean StreamService stream;
    @Test void refusesMissingCredentials() throws Exception {mvc.perform(post("/api/stats")).andExpect(status().isUnauthorized());}
    @Test void refusesWrongCredentials() throws Exception {mvc.perform(post("/api/stats").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());}
    @Test void acceptsConfiguredBearerCredential() throws Exception {mvc.perform(post("/api/stats").header("Authorization","Bearer isolated-test-token-32-characters-minimum")).andExpect(status().isOk());}
}
