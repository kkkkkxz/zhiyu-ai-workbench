package com.zwskchat.zwskchat.user.service;

import com.zwskchat.zwskchat.user.entity.SysUser;

import java.util.List;

public interface SysUserService {

    /**
     * 不区分租户按 username 查找。多行命中时返回 {@code null} 并记录 WARN 日志，
     * 避免 MyBatis-Plus 的 selectOne 抛 TooManyResultsException 让 HTTP 直接 500。
     * <p>调用方收到 null 时如果想区分"不存在"和"歧义"，请改用 {@link #findAllByUsername}。</p>
     */
    SysUser findByUsername(String username);

    /**
     * 跨租户列出所有同名用户。用于登录链路判断是否需要 tenantCode 消歧。
     */
    List<SysUser> findAllByUsername(String username);

    /**
     * 按 (租户编码, 用户名) 精确查找。任一不匹配（包括租户已停用 / 软删除）返回 null。
     */
    SysUser findByTenantCodeAndUsername(String tenantCode, String username);

    SysUser findByTenantAndId(Long tenantId, Long userId);

    /**
     * Legacy helper. In authenticated request scope it is tenant-scoped by the
     * current TenantContext; new auth-sensitive code should prefer
     * {@link #findByTenantAndId(Long, Long)}.
     */
    SysUser findById(Long id);

    void updateLastLogin(Long tenantId, Long userId, String ip);
}
