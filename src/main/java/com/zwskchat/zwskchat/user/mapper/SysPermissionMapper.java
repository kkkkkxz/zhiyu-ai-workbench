package com.zwskchat.zwskchat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.user.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("SELECT DISTINCT p.* FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.permission_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.tenant_id = #{tenantId} " +
            "  AND r.tenant_id = #{tenantId} " +
            "  AND ur.user_id = #{userId} " +
            "  AND r.deleted = 0 AND r.status = 1 " +
            "  AND p.status = 1")
    List<SysPermission> selectPermissionsByTenantAndUserId(@Param("tenantId") Long tenantId,
                                                           @Param("userId") Long userId);
}
