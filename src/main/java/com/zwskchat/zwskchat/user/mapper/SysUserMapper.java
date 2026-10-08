package com.zwskchat.zwskchat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按 (租户编码, 用户名) 精确查找用户。
     *
     * <p>用于多租户登录场景：登录请求携带 tenantCode 时按 sys_tenant.code 与 sys_user.username
     * 双条件命中。sys_tenant 和 sys_user 都需要 tenant 拦截器忽略（前者本身在 ignore-tables，
     * 后者通过调用方 setIgnore(true) 处理），sys_user.tenant_id = sys_tenant.id 由 join 保证。</p>
     */
    @Select("SELECT u.* FROM sys_user u " +
            "JOIN sys_tenant t ON t.id = u.tenant_id " +
            "WHERE u.username = #{username} " +
            "  AND t.code = #{tenantCode} " +
            "  AND u.deleted = 0 " +
            "  AND t.deleted = 0 " +
            "LIMIT 1")
    SysUser selectByTenantCodeAndUsername(@Param("tenantCode") String tenantCode,
                                          @Param("username") String username);

    /**
     * 检查同租户内 username 是否已被占用。
     *
     * <p>注册路径专用：唯一约束 {@code uk_tenant_username (tenant_id, username, deleted)}
     * 已经在 DB 层兜底，但提前用 COUNT 给前端友好提示，避免 race 后还是抛 SQLIntegrityConstraintViolation。</p>
     */
    @Select("SELECT COUNT(*) FROM sys_user " +
            "WHERE tenant_id = #{tenantId} AND username = #{username} AND deleted = 0")
    int countByTenantAndUsername(@Param("tenantId") Long tenantId,
                                 @Param("username") String username);

    @Select("SELECT * FROM sys_user " +
            "WHERE id = #{userId} AND tenant_id = #{tenantId} AND deleted = 0 " +
            "LIMIT 1")
    SysUser selectByTenantAndId(@Param("tenantId") Long tenantId,
                                @Param("userId") Long userId);
}
