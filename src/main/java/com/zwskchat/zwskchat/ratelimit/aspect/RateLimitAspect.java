package com.zwskchat.zwskchat.ratelimit.aspect;

import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.constant.CacheKeys;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.common.util.IpUtils;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import com.zwskchat.zwskchat.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = buildScript();

    private static DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> s = new DefaultRedisScript<>();
        s.setResultType(Long.class);
        s.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rate_limit.lua")));
        return s;
    }

    /**
     * Match either method-level or class-level {@code @RateLimit}.
     *
     * <p>We deliberately do <b>not</b> bind {@code rl} via {@code @annotation(rl) || @within(rl)},
     * because AspectJ's disjunctive binding silently passes {@code null} for the branch
     * that did not match. Instead we look up the annotation manually with
     * {@link AnnotationUtils} which transparently handles meta-annotations,
     * inheritance and Spring's CGLIB proxies.</p>
     */
    @Around("@annotation(com.zwskchat.zwskchat.ratelimit.annotation.RateLimit) "
            + "|| @within(com.zwskchat.zwskchat.ratelimit.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        RateLimit rl = resolveAnnotation(pjp);
        if (rl == null) return pjp.proceed();
        String dimValue = resolveDimension(rl);
        String key = CacheKeys.RATE_LIMIT + rl.key() + ":" + dimValue;
        Long pass;
        try {
            pass = redis.execute(script, Collections.singletonList(key),
                    String.valueOf(rl.limit()), String.valueOf(rl.window()));
        } catch (DataAccessException e) {
            // Redis 不可用 / MISCONF / Lua 执行失败 → 降级为放行+告警，避免拖垮业务
            log.warn("[RateLimit] redis unavailable, fail-open. key={} dim={} err={}",
                    rl.key(), dimValue, e.getMessage());
            return pjp.proceed();
        }
        if (pass == null || pass == 0L) {
            log.warn("[RateLimit] key={} dim={} hit limit={}/{}s", rl.key(), dimValue, rl.limit(), rl.window());
            throw new BusinessException(ResultCode.RATE_LIMITED);
        }
        return pjp.proceed();
    }

    /**
     * Method-level {@code @RateLimit} wins over class-level. Uses
     * {@link AnnotationUtils#findAnnotation(Method, Class)} so that meta-annotations
     * and Spring's CGLIB-proxied targets are handled correctly.
     */
    private RateLimit resolveAnnotation(ProceedingJoinPoint pjp) {
        if (!(pjp.getSignature() instanceof MethodSignature ms)) return null;
        Method method = ms.getMethod();
        RateLimit rl = AnnotationUtils.findAnnotation(method, RateLimit.class);
        if (rl != null) return rl;
        Class<?> targetClass = pjp.getTarget() == null ? method.getDeclaringClass() : pjp.getTarget().getClass();
        return AnnotationUtils.findAnnotation(targetClass, RateLimit.class);
    }

    private String resolveDimension(RateLimit rl) {
        return switch (rl.dimension()) {
            case USER -> {
                Long uid = SecurityUtils.currentUserIdOrNull();
                if (uid != null) yield uid.toString();
                // 匿名访问：不能让所有匿名用户共享同一个 "anon" 桶（一个攻击者打满会让所有人都触发限流）。
                // 回退到 IP 维度，每个客户端按自己 IP 独立限流。
                yield "anon@" + currentClientIpOrUnknown();
            }
            case IP -> currentClientIpOrUnknown();
            case GLOBAL -> "global";
        };
    }

    private String currentClientIpOrUnknown() {
        ServletRequestAttributes attr = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        HttpServletRequest req = attr == null ? null : attr.getRequest();
        String ip = IpUtils.getClientIp(req);
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
