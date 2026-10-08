package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.common.constant.SecurityConstants;
import com.zwskchat.zwskchat.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwt;
    private final TokenStore tokenStore;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        try {
            try {
                authenticate(req);
            } catch (Exception e) {
                // 1) Token 已被注销 / 用户被禁用 / 账号被删除等情况不能把整个请求打成 500；
                // 2) 也不直接 401，交给 Spring Security 的 ExceptionTranslationFilter 根据匿名
                //    访问权限统一走 AuthenticationEntryPoint，行为与"无 token"一致。
                SecurityContextHolder.clearContext();
                TenantContext.clear();
                log.debug("[JwtAuthFilter] authenticate failed, fall through as anonymous: {}", e.getMessage());
            }
            chain.doFilter(req, resp);
        } finally {
            TenantContext.clear();
        }
    }

    private void authenticate(HttpServletRequest req) {
        String token = resolveToken(req);
        if (token == null) return;
        Claims claims = jwt.parse(token);
        if (claims == null
                || !SecurityConstants.TOKEN_TYPE_ACCESS.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE))) {
            return;
        }
        String jti = claims.getId();
        if (jti == null || !tokenStore.isAccessActive(jti) || tokenStore.isBlacklisted(jti)) {
            return;
        }
        Number tid = claims.get(SecurityConstants.CLAIM_TENANT_ID, Number.class);
        Number uid = claims.get(SecurityConstants.CLAIM_USER_ID, Number.class);
        String username = claims.get(SecurityConstants.CLAIM_USERNAME, String.class);
        if (tid == null || uid == null || username == null || username.isBlank()) return;

        Long tenantId = tid.longValue();
        Long userId = uid.longValue();
        TenantContext.setTenantId(tenantId);
        UserDetails ud = userDetailsService.loadUserByTenantAndId(tenantId, userId);
        if (ud == null || !ud.isEnabled() || !ud.isAccountNonLocked()) return;
        if (ud instanceof LoginUser loginUser && !username.equals(loginUser.getUsername())) return;

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String resolveToken(HttpServletRequest req) {
        String h = req.getHeader(SecurityConstants.HEADER_AUTH);
        if (h != null && h.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return h.substring(SecurityConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}
