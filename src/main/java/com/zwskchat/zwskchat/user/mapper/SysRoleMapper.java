package com.zwskchat.zwskchat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("SELECT r.* FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.tenant_id = #{tenantId} " +
            "  AND r.tenant_id = #{tenantId} " +
            "  AND ur.user_id = #{userId} " +
            "  AND r.deleted = 0 AND r.status = 1")
    List<SysRole> selectRolesByTenantAndUserId(@Param("tenantId") Long tenantId,
                                               @Param("userId") Long userId);

    /**
     * 按 (租户 ID, 角色编码) 精确查找。注册路径用来取默认 STAFF 角色 id，避免硬编码。
     */
    @Select("SELECT * FROM sys_role " +
            "WHERE tenant_id = #{tenantId} AND role_code = #{roleCode} " +
            "  AND deleted = 0 AND status = 1 " +
            "LIMIT 1")
    SysRole selectByTenantAndCode(@Param("tenantId") Long tenantId,
                                  @Param("roleCode") String roleCode);

    @Select("SELECT * FROM sys_role " +
            "WHERE tenant_id = #{tenantId} AND deleted = 0 AND status = 1 " +
            "ORDER BY id ASC")
    List<SysRole> selectEnabledByTenant(@Param("tenantId") Long tenantId);
}
