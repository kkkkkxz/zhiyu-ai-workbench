package com.zwskchat.zwskchat.common.constant;

/**
 * Redis Key 命名规范：模块:子模块:维度:具体值
 * 严禁存放明文敏感数据；TTL 必须设置。
 */
public final class CacheKeys {

    private CacheKeys() {}

    /** 访问 Token 状态：auth:token:{tokenId} = userId （TTL=访问令牌有效期） */
    public static final String AUTH_TOKEN = "auth:token:";

    /** 刷新 Token：auth:refresh:{tokenId} = userId */
    public static final String AUTH_REFRESH = "auth:refresh:";

    /** Token 黑名单：auth:blacklist:{tokenId} = "1" */
    public static final String AUTH_BLACKLIST = "auth:blacklist:";

    /** 登录失败计数：auth:login:fail:{username} */
    public static final String LOGIN_FAIL = "auth:login:fail:";

    /** 账号锁定：auth:login:lock:{username} */
    public static final String LOGIN_LOCK = "auth:login:lock:";

    /** 用户权限缓存：user:perm:{userId} */
    public static final String USER_PERM = "user:perm:";

    /** 用户角色缓存：user:roles:{userId} */
    public static final String USER_ROLES = "user:roles:";

    /** 限流计数：rl:{key} */
    public static final String RATE_LIMIT = "rl:";

    /** RAG 检索缓存：rag:q:{tenantId}:{kbHash}:{questionHash} */
    public static final String RAG_QUERY = "rag:q:";

    /** WebSocket 会话状态：ws:{sessionId} */
    public static final String WS_SESSION = "ws:";

    /** SSO 一次性登录票据：auth:sso:ticket:{ticket} = userId（短 TTL，一次性消费） */
    public static final String SSO_TICKET = "auth:sso:ticket:";

    public static String authToken(String tokenId) { return AUTH_TOKEN + tokenId; }
    public static String authRefresh(String tokenId) { return AUTH_REFRESH + tokenId; }
    public static String authBlacklist(String tokenId) { return AUTH_BLACKLIST + tokenId; }

    /**
     * 登录失败计数 / 锁定 key 携带租户编码，避免跨租户的同名用户共享同一个失败桶——
     * 否则租户 A 的 admin 失败 5 次会让租户 B 的 admin 一起被锁。
     * <p>{@code tenantCode} 为空时使用占位符 "_"（兼容单租户假设：未传 tenantCode 的所有 admin
     * 共享一个桶，等同旧行为；多租户接入后强制传 tenantCode 即可天然隔离）。</p>
     */
    public static String loginFail(String tenantCode, String username) {
        return LOGIN_FAIL + tenantSlot(tenantCode) + ":" + username;
    }
    public static String loginLock(String tenantCode, String username) {
        return LOGIN_LOCK + tenantSlot(tenantCode) + ":" + username;
    }
    private static String tenantSlot(String tenantCode) {
        return (tenantCode == null || tenantCode.isBlank()) ? "_" : tenantCode.trim();
    }

    public static String userPerm(Long userId) { return USER_PERM + userId; }
    public static String userRoles(Long userId) { return USER_ROLES + userId; }

    public static String ssoTicket(String ticket) { return SSO_TICKET + ticket; }
}
