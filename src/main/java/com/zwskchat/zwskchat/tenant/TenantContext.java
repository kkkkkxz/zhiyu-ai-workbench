package com.zwskchat.zwskchat.tenant;

/**
 * 线程级租户上下文。由 JwtAuthenticationFilter 在认证后写入，请求结束后清理。
 * setIgnore(true) 可临时跳过租户过滤（仅供后台跨租户管理使用）。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TenantContext() {}

    public static void setTenantId(Long id) { TENANT.set(id); }
    public static Long getTenantId() { return TENANT.get(); }

    public static Long requireTenantId() {
        Long id = TENANT.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext is missing");
        }
        return id;
    }

    public static void setIgnore(boolean ignore) { IGNORE.set(ignore); }
    public static boolean isIgnore() { return Boolean.TRUE.equals(IGNORE.get()); }

    public static Scope withTenant(Long tenantId) {
        Long previousTenant = TENANT.get();
        Boolean previousIgnore = IGNORE.get();
        TENANT.set(tenantId);
        IGNORE.set(Boolean.FALSE);
        return new Scope(previousTenant, previousIgnore);
    }

    public static Scope ignore() {
        Long previousTenant = TENANT.get();
        Boolean previousIgnore = IGNORE.get();
        IGNORE.set(Boolean.TRUE);
        return new Scope(previousTenant, previousIgnore);
    }

    public static void clear() {
        TENANT.remove();
        IGNORE.remove();
    }

    public static final class Scope implements AutoCloseable {
        private final Long previousTenant;
        private final Boolean previousIgnore;
        private boolean closed;

        private Scope(Long previousTenant, Boolean previousIgnore) {
            this.previousTenant = previousTenant;
            this.previousIgnore = previousIgnore;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (previousTenant == null) TENANT.remove();
            else TENANT.set(previousTenant);
            if (previousIgnore == null) IGNORE.remove();
            else IGNORE.set(previousIgnore);
        }
    }
}
