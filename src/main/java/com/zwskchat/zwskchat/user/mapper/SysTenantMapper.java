package com.zwskchat.zwskchat.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /** 按租户编码查找；找不到 / 已停用 / 软删除 → null。 */
    @Select("SELECT * FROM sys_tenant " +
            "WHERE code = #{code} AND deleted = 0 AND status = 1 " +
            "LIMIT 1")
    SysTenant selectByCode(@Param("code") String code);

    /**
     * 取系统默认租户（id 最小的启用租户）。
     *
     * <p>注册请求未带 tenantCode 时使用——单租户场景下基本就是历史 1 号租户。</p>
     */
    @Select("SELECT * FROM sys_tenant " +
            "WHERE deleted = 0 AND status = 1 " +
            "ORDER BY id ASC LIMIT 1")
    SysTenant selectDefault();

    @Select("SELECT * FROM sys_tenant " +
            "WHERE id = #{tenantId} AND deleted = 0 " +
            "LIMIT 1")
    SysTenant selectExistingById(@Param("tenantId") Long tenantId);
}
