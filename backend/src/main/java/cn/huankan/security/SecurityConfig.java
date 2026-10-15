package cn.huankan.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean org.springframework.security.core.userdetails.UserDetailsService users() {
        return username -> {throw new org.springframework.security.core.userdetails.UsernameNotFoundException("仅支持配置的本地访问密钥");};
    }
    @Bean SecurityFilterChain security(HttpSecurity http, @Value("${huankan.token}") String token) throws Exception {
        if (token.length() < 32) throw new IllegalArgumentException("管理密钥至少32个字符");
        var filter = new OncePerRequestFilter() {
            @Override protected boolean shouldNotFilterAsyncDispatch() {return false;}
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                String auth = request.getHeader("Authorization");
                if (auth != null && MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8), ("Bearer " + token).getBytes(StandardCharsets.UTF_8))) {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("local-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                }
                chain.doFilter(request, response);
            }
        };
        return http.csrf(c -> c.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers("/actuator/health").permitAll().anyRequest().hasRole("ADMIN"))
            .exceptionHandling(e -> e.authenticationEntryPoint((r,s,x) -> s.sendError(401, "请提供访问密钥")))
            .addFilterBefore(new RequestRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
