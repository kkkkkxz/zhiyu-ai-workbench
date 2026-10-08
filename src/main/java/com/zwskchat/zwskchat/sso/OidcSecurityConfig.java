package com.zwskchat.zwskchat.sso;

import com.zwskchat.zwskchat.auth.dto.LoginResponse;
import com.zwskchat.zwskchat.auth.service.AuthService;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

/**
 * OIDC 单点登录链路（对应工程计划书“OAuth 2.0 + OIDC 接入企业统一身份源”）。
 *
 * <p><b>仅在 'oidc' profile 激活时装配</b>，与默认 {@code SecurityConfig} 的自管 JWT 链路并存：
 * 本链路用 {@link Order}=1 优先匹配 {@code /oauth2/**} 与 {@code /login/oauth2/**}，走标准
 * authorization_code 流程；其余请求仍由默认链路（JWT）处理。</p>
 *
 * <p>登录成功后，{@link #oidcSuccessHandler} 把 IdP 身份映射到本系统用户、换取本系统 JWT，
 * 再 302 跳回前端并以 query 形式回传 token——前端拿到后存本地，后续请求仍用本系统 JWT，
 * 因此业务接口的鉴权逻辑完全不变。</p>
 */
@Slf4j
@Configuration
@Profile("oidc")
@RequiredArgsConstructor
public class OidcSecurityConfig {

    private final OidcProperties oidcProps;
    private final AuthService authService;

    @Bean
    @Order(1)
    public SecurityFilterChain oidcFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        .successHandler(this::oidcSuccessHandler)
                        .failureHandler(new SimpleUrlAuthenticationFailureHandler(oidcProps.getFailureRedirect())));
        return http.build();
    }

    /**
     * OIDC 登录成功 → 映射本系统用户 → 颁发本系统 JWT → 302 回传前端。
     */
    private void oidcSuccessHandler(HttpServletRequest request, HttpServletResponse response,
                                    org.springframework.security.core.Authentication authentication) throws IOException {
        try {
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof OidcUser oidcUser)) {
                throw new BusinessException(com.zwskchat.zwskchat.common.api.ResultCode.TOKEN_INVALID,
                        "OIDC 主体类型异常");
            }
            String username = claim(oidcUser, oidcProps.getUsernameClaim());
            if (username == null || username.isBlank()) {
                // 回退到标准 OIDC 字段
                username = oidcUser.getPreferredUsername() != null ? oidcUser.getPreferredUsername()
                        : oidcUser.getEmail() != null ? oidcUser.getEmail() : oidcUser.getSubject();
            }
            String tenantCode = oidcProps.getTenantClaim() == null || oidcProps.getTenantClaim().isBlank()
                    ? oidcProps.getDefaultTenantCode()
                    : claim(oidcUser, oidcProps.getTenantClaim());

            LoginResponse token = authService.oidcLogin(tenantCode, username, request);

            String redirect = UriComponentsBuilder.fromUriString(oidcProps.getSuccessRedirect())
                    .queryParam("token", URLEncoder.encode(token.getAccessToken(), StandardCharsets.UTF_8))
                    .queryParam("refreshToken", URLEncoder.encode(token.getRefreshToken(), StandardCharsets.UTF_8))
                    .build(true)
                    .toUriString();
            response.sendRedirect(redirect);
        } catch (BusinessException e) {
            log.warn("[OIDC] success handler mapping failed: {}", e.getMessage());
            response.sendRedirect(oidcProps.getFailureRedirect());
        } catch (Exception e) {
            log.error("[OIDC] success handler error", e);
            response.sendRedirect(oidcProps.getFailureRedirect());
        }
    }

    private static String claim(OidcUser user, String name) {
        if (name == null || name.isBlank()) return null;
        Object v = user.getClaims().get(name);
        return v == null ? null : v.toString();
    }
}
