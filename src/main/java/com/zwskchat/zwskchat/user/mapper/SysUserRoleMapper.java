package com.zwskchat.zwskchat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("SELECT ur.* FROM sys_user_role ur " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE ur.tenant_id = #{tenantId} " +
            "  AND ur.user_id = #{userId} " +
            "  AND r.tenant_id = #{tenantId} " +
            "  AND r.deleted = 0")
    List<SysUserRole> selectByTenantAndUserId(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId);
}
