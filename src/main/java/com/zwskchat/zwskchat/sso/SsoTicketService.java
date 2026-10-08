package com.zwskchat.zwskchat.sso;

import com.zwskchat.zwskchat.common.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 内部单点登录（SSO）票据服务：自包含实现，不依赖外部 IdP。
 *
 * <p>已登录用户调用 {@link #issue(Long)} 取得一张短期、一次性的 ticket；其他子系统/客户端
 * 用该 ticket 调用 SSO 交换接口换取本系统 JWT，从而实现“一次登录、多处免登”。
 * ticket 仅在 Redis 存活 {@link #TTL_SECONDS} 秒，且消费即删除（一次性），降低泄漏风险。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SsoTicketService {

    /** 票据有效期（秒）：短时有效，仅用于"刚登录 → 立刻换 token"窗口。 */
    public static final long TTL_SECONDS = 120;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /** 为已登录用户签发一次性 SSO 票据。 */
    public String issue(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenantId / userId 不能为空");
        }
        String ticket = randomTicket();
        redis.opsForValue().set(CacheKeys.ssoTicket(ticket), tenantId + ":" + userId,
                Duration.ofSeconds(TTL_SECONDS));
        log.info("[SSO] issued ticket for tenantId={}, userId={}", tenantId, userId);
        return ticket;
    }

    /**
     * 消费票据：返回对应 (tenantId, userId) 并立即删除（一次性）。无效/过期/已用返回 null。
     * 使用 getAndDelete 保证并发下同一票据只能被成功兑换一次。
     */
    public SsoPrincipal consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return null;
        String key = CacheKeys.ssoTicket(ticket);
        String value = redis.opsForValue().getAndDelete(key);
        if (value == null) {
            log.warn("[SSO] ticket invalid or already consumed");
            return null;
        }
        int sep = value.indexOf(':');
        if (sep <= 0 || sep == value.length() - 1) return null;
        try {
            long tenantId = Long.parseLong(value.substring(0, sep));
            long userId = Long.parseLong(value.substring(sep + 1));
            return new SsoPrincipal(tenantId, userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 票据携带的主体标识。 */
    public record SsoPrincipal(Long tenantId, Long userId) {
    }

    private static String randomTicket() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
