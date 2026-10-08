package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.config.properties.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties props;
    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(props.getPassword().getBcryptStrength());
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /** 阻止 Spring Boot 将 JWT 过滤器作为普通 Servlet Filter 重复注册 */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> permit = props.getPermitUrls();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(req -> {
                    CorsConfiguration cc = new CorsConfiguration();
                    List<String> origins = props.getCors().getAllowedOrigins();
                    if (origins != null) {
                        origins = origins.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toList();
                    }
                    if (origins == null || origins.isEmpty()) {
                        // 防御性默认：未配置任何 origin 时不放行，避免意外全开。
                        origins = List.of("http://localhost:3000", "http://127.0.0.1:3000");
                    }
                    cc.setAllowedOriginPatterns(origins);
                    cc.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
                    cc.setAllowedHeaders(List.of("*"));
                    cc.setAllowCredentials(true);
                    cc.setExposedHeaders(List.of("X-Trace-Id"));
                    cc.setMaxAge(3600L);
                    return cc;
                }))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(h -> h
                        // 防点击劫持：禁止本应用被任何域 iframe 嵌入
                        .frameOptions(f -> f.deny())
                        // 防 MIME 嗅探：浏览器严格按 Content-Type 处理资源
                        .contentTypeOptions(c -> {})
                        // 跨域跳转时只发送 origin 信息（避免 query string 里 token / 业务参数泄漏）
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // HSTS：强制浏览器在后续 12 个月只用 HTTPS 访问本域。
                        // 仅当 https 才生效；HTTP 部署忽略本头。生产部署必须前置 HTTPS 网关。
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000L))
                        // CSP：纵深防御。即使页面有 XSS，也限制脚本只能来自本域 + 必要的 inline。
                        // 当前 React + Vite 构建产物内联 module 脚本、tailwind inline style，
                        // 所以 'unsafe-inline' / 'unsafe-eval' 暂时保留。后端 SPA 部署时
                        // 通过 nonce 进一步收紧（建议下一迭代）。
                        // connect-src 同时放行 wss/https，覆盖语音 WebSocket 与外部模型 API。
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data: blob:; "
                                        + "media-src 'self' data: blob:; "
                                        + "connect-src 'self' ws: wss: https:; "
                                        + "font-src 'self' data:; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'; "
                                        + "form-action 'self'"))
                        // 历史遗留 XSS Auditor 头，新浏览器忽略，老浏览器二道兜底
                        .xssProtection(x -> x.headerValue(
                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)))
                .authorizeHttpRequests(auth -> auth
                        // SSE / @Async 内部 dispatch 不再二次鉴权（原 REQUEST 已认证）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC,
                                                DispatcherType.ERROR,
                                                DispatcherType.FORWARD,
                                                DispatcherType.INCLUDE).permitAll()
                        .requestMatchers(permit.toArray(new String[0])).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
