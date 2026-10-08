package com.zwskchat.zwskchat.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流：基于 Redis Lua 滑动窗口计数。
 * 命中限流时返回 8001 RATE_LIMITED。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RateLimit {

    /** 业务 key（命名空间），最终 Redis Key = rl:{key}:{dim}:{window} */
    String key();

    /** 窗口内最大请求数 */
    int limit() default 20;

    /** 窗口大小（秒） */
    int window() default 1;

    /** 维度：用户 / IP / 全局 */
    Dimension dimension() default Dimension.USER;

    enum Dimension { USER, IP, GLOBAL }
}
