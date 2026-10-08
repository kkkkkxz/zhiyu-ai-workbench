package com.zwskchat.zwskchat.voice.v3.security;

import com.zwskchat.zwskchat.common.constant.SecurityConstants;
import com.zwskchat.zwskchat.common.util.IpUtils;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.security.JwtTokenProvider;
import com.zwskchat.zwskchat.security.TokenStore;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceV3HandshakeInterceptor implements HandshakeInterceptor {

    /** Redis key 前缀；与 RateLimitAspect 共享 namespace 但加 'jarvis:' 区分专属桶。 */
    private static final String JARVIS_IP_KEY_PREFIX = "rl:jarvis:ip:";

    /** 自定义 HTTP 状态码 429（Too Many Requests）。 */
    private static final HttpStatus TOO_MANY_REQUESTS = HttpStatus.valueOf(429);

    private final JwtTokenProvider jwt;
    private final TokenStore tokenStore;
    private final VoiceProperties voiceProperties;
    private final StringRedisTemplate redis;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest s)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        String publicAccess = s.getServletRequest().getParameter("public");
        if ("1".equals(publicAccess) || "true".equalsIgnoreCase(publicAccess)) {
            VoiceProperties.PublicJarvis cfg = voiceProperties.getPublicJarvis();
            if (cfg == null || !cfg.isEnabled()) {
                // 默认行为：禁用免登录入口，避免任何人都能消费 LLM/TTS。
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            // IP 维度限流：每 IP 在 connectionWindowSeconds 内最多 maxConnectionsPerIp 次握手。
            // 配合 VoiceSessionV3 的 maxMessagesPerConnection（单连接消息上限），
            // 给匿名通道一个明确的"预算"上限，挡住脚本批量耗尽 LLM 配额。
            String clientIp = IpUtils.getClientIp(s.getServletRequest());
            if (!allowJarvisHandshake(cfg, clientIp)) {
                log.warn("[Voice-V3] public-jarvis handshake throttled by IP, ip={} limit={}/{}s",
                        clientIp, cfg.getMaxConnectionsPerIp(), cfg.getConnectionWindowSeconds());
                response.setStatusCode(TOO_MANY_REQUESTS);
                return false;
            }
            attributes.put("userId", cfg.getUserId() == null ? 0L : cfg.getUserId());
            // 注意：tenantId 默认 0L 表示"匿名占位"，不和真实租户共用边界；
            // 只有当运维显式配置了专用 demo 租户时才会取到非 0 值。
            attributes.put("tenantId", cfg.getTenantId() == null ? 0L : cfg.getTenantId());
            attributes.put("username", cfg.getUsername() == null || cfg.getUsername().isBlank() ? "jarvis-public" : cfg.getUsername());
            attributes.put("publicJarvis", Boolean.TRUE);
            // 把单连接消息预算放进 attributes，VoiceSessionV3 / orchestrator 用它做软上限
            attributes.put("publicJarvisMaxMessages", cfg.getMaxMessagesPerConnection());
            return true;
        }

        String token = s.getServletRequest().getParameter("token");
        if (token == null || token.isBlank()) {
            String h = s.getServletRequest().getHeader(SecurityConstants.HEADER_AUTH);
            if (h != null && h.startsWith(SecurityConstants.TOKEN_PREFIX)) {
                token = h.substring(SecurityConstants.TOKEN_PREFIX.length());
            }
        }
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Claims c = jwt.parse(token);
        if (c == null || !SecurityConstants.TOKEN_TYPE_ACCESS.equals(c.get(SecurityConstants.CLAIM_TOKEN_TYPE))) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String jti = c.getId();
        if (jti == null || !tokenStore.isAccessActive(jti) || tokenStore.isBlacklisted(jti)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        Number uid = c.get(SecurityConstants.CLAIM_USER_ID, Number.class);
        Number tid = c.get(SecurityConstants.CLAIM_TENANT_ID, Number.class);
        String name = c.get(SecurityConstants.CLAIM_USERNAME, String.class);
        attributes.put("userId", uid == null ? null : uid.longValue());
        attributes.put("tenantId", tid == null ? null : tid.longValue());
        attributes.put("username", name);
        attributes.put("publicJarvis", Boolean.FALSE);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception ex) {
        if (ex != null) log.warn("[Voice-V3] handshake error: {}", ex.getMessage());
    }

    /**
     * 用 Redis INCR + EXPIRE 实现固定窗口计数器，对同一 IP 的 Jarvis 握手做限流。
     *
     * <p><b>容错策略：</b>Redis 不可用时降级为放行 + 告警（fail-open），
     * 与 {@code RateLimitAspect} 行为对齐。这避免 Redis 短暂故障让所有公网用户都连不上，
     * 但同时意味着 Redis 出问题时本层防护暂时不生效——上游运维监控应能感知到。</p>
     */
    private boolean allowJarvisHandshake(VoiceProperties.PublicJarvis cfg, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) clientIp = "unknown";
        int limit = Math.max(1, cfg.getMaxConnectionsPerIp());
        int windowSec = Math.max(1, cfg.getConnectionWindowSeconds());
        String key = JARVIS_IP_KEY_PREFIX + clientIp;
        try {
            Long current = redis.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redis.expire(key, Duration.ofSeconds(windowSec));
            }
            return current == null || current <= limit;
        } catch (DataAccessException e) {
            log.warn("[Voice-V3] redis unavailable, jarvis handshake fail-open. ip={} err={}",
                    clientIp, e.getMessage());
            return true;
        }
    }
}
