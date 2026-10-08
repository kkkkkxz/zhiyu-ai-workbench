package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.common.constant.SecurityConstants;
import com.zwskchat.zwskchat.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    /** 与 application.yaml 默认值前缀对齐；只要生产 profile 还能匹配到这个前缀，就立刻报错启动失败。 */
    private static final String DEV_DEFAULT_SECRET_PREFIX = "dev-only-secret";

    /** 视为"非生产"的 profile 列表；落到这些 profile 时允许使用默认密钥跑开发自测。 */
    private static final Set<String> NON_PROD_PROFILES = Set.of("dev", "test", "local", "demo");

    private final JwtProperties props;
    private final Environment env;
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        String secret = props.getSecret();
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret 长度不足，必须 >= 32 字节，请通过环境变量 JWT_SECRET 注入");
        }

        // 生产强制校验：默认密钥只允许在 dev/test/local/demo profile 下使用。
        // 这里特意写得保守——只要 active profile 中有任何一个不在白名单里，就视为"可能是生产"，
        // 必须显式覆盖密钥。这样即使运维忘了设 SPRING_PROFILES_ACTIVE=prod 也不会被绕过。
        if (secret != null && secret.startsWith(DEV_DEFAULT_SECRET_PREFIX)) {
            String[] active = env.getActiveProfiles();
            boolean allDev = active.length > 0
                    && Arrays.stream(active).allMatch(NON_PROD_PROFILES::contains);
            if (!allDev) {
                throw new IllegalStateException(
                        "检测到 JWT secret 仍是开发默认值，但当前 profile=" + Arrays.toString(active)
                                + " 不在白名单 " + NON_PROD_PROFILES
                                + "。生产环境必须通过环境变量 JWT_SECRET 注入随机 64 字节密钥，"
                                + "否则任何人都能伪造 token 冒充任意用户。");
            }
            log.warn("[JWT] using DEV default secret (profile={}). 生产环境部署务必通过 JWT_SECRET 注入随机密钥。",
                    Arrays.toString(active));
        }

        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public TokenPair generate(LoginUser user) {
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Date accessExp = new Date(now + props.getAccessTokenTtlSeconds() * 1000L);
        Date refreshExp = new Date(now + props.getRefreshTokenTtlSeconds() * 1000L);

        String access = Jwts.builder()
                .id(accessJti)
                .issuer(props.getIssuer())
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(new Date(now))
                .expiration(accessExp)
                .claim(SecurityConstants.CLAIM_USER_ID, user.getUserId())
                .claim(SecurityConstants.CLAIM_TENANT_ID, user.getTenantId())
                .claim(SecurityConstants.CLAIM_TENANT_CODE, user.getTenantCode())
                .claim(SecurityConstants.CLAIM_USERNAME, user.getUsername())
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_ACCESS)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        String refresh = Jwts.builder()
                .id(refreshJti)
                .issuer(props.getIssuer())
                .subject(String.valueOf(user.getUserId()))
                .issuedAt(new Date(now))
                .expiration(refreshExp)
                .claim(SecurityConstants.CLAIM_USER_ID, user.getUserId())
                .claim(SecurityConstants.CLAIM_TENANT_ID, user.getTenantId())
                .claim(SecurityConstants.CLAIM_TENANT_CODE, user.getTenantCode())
                .claim(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_REFRESH)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new TokenPair(access, accessJti, accessExp.getTime(),
                refresh, refreshJti, refreshExp.getTime());
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            log.debug("JWT parse failed: {}", e.getMessage());
            return null;
        }
    }

    public long getAccessTtlSeconds() { return props.getAccessTokenTtlSeconds(); }
    public long getRefreshTtlSeconds() { return props.getRefreshTokenTtlSeconds(); }

    public record TokenPair(String accessToken, String accessJti, long accessExpiresAt,
                            String refreshToken, String refreshJti, long refreshExpiresAt) {}
}
