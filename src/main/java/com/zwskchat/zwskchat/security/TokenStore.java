package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.common.constant.CacheKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token 状态存储：登录成功后 jti 写入 Redis，注销时进入黑名单。
 * 网关或 Filter 校验 Token 时必须额外查询黑名单。
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private final StringRedisTemplate redis;

    public void saveAccess(String jti, Long userId, long ttlSeconds) {
        redis.opsForValue().set(CacheKeys.authToken(jti), String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    public void saveRefresh(String jti, Long userId, long ttlSeconds) {
        redis.opsForValue().set(CacheKeys.authRefresh(jti), String.valueOf(userId), Duration.ofSeconds(ttlSeconds));
    }

    public boolean isAccessActive(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(CacheKeys.authToken(jti)));
    }

    public boolean isRefreshActive(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(CacheKeys.authRefresh(jti)));
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(CacheKeys.authBlacklist(jti)));
    }

    public void blacklist(String jti, long remainingSeconds) {
        if (remainingSeconds <= 0) return;
        redis.opsForValue().set(CacheKeys.authBlacklist(jti), "1", Duration.ofSeconds(remainingSeconds));
    }

    public void revokeAccess(String jti) {
        redis.delete(CacheKeys.authToken(jti));
    }

    public void revokeRefresh(String jti) {
        redis.delete(CacheKeys.authRefresh(jti));
    }
}
