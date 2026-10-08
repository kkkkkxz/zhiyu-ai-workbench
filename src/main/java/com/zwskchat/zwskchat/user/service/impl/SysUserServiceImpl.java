package com.zwskchat.zwskchat.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.user.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper userMapper;

    @Override
    public SysUser findByUsername(String username) {
        // sys_user 唯一约束是 (tenant_id, username, deleted)，跨租户可能同名。
        // 之前用 selectOne 在多租户接入后会抛 TooManyResultsException 让 500。
        // 现改成 selectList 防御：0 行 null、1 行返回那行、>1 行返回 null + WARN 日志，
        // 同时让调用方有机会通过 findAllByUsername 走"消歧路径"（要求传 tenantCode）。
        List<SysUser> rows = findAllByUsername(username);
        if (rows.isEmpty()) return null;
        if (rows.size() == 1) return rows.get(0);
        log.warn("[SysUser] username='{}' matches {} tenants, returning null to force ambiguity handling. tenantIds={}",
                username, rows.size(),
                rows.stream().map(SysUser::getTenantId).toList());
        return null;
    }

    @Override
    public List<SysUser> findAllByUsername(String username) {
        if (username == null || username.isBlank()) return Collections.emptyList();
        // 登录路径 TenantContext 还没建立（或不应被限定到某一租户），临时跳过拦截器自动注入。
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysUser> list = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, username));
            return list == null ? Collections.emptyList() : list;
        }
    }

    @Override
    public SysUser findByTenantCodeAndUsername(String tenantCode, String username) {
        if (tenantCode == null || tenantCode.isBlank() || username == null || username.isBlank()) {
            return null;
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            return userMapper.selectByTenantCodeAndUsername(tenantCode.trim(), username);
        }
    }

    @Override
    public SysUser findByTenantAndId(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return null;
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            return userMapper.selectByTenantAndId(tenantId, userId);
        }
    }

    @Override
    public SysUser findById(Long id) {
        if (id == null) return null;
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return userMapper.selectByTenantAndId(tenantId, id);
        }
        return userMapper.selectById(id);
    }

    @Override
    public void updateLastLogin(Long tenantId, Long userId, String ip) {
        if (tenantId == null || userId == null) return;
        SysUser u = new SysUser();
        u.setId(userId);
        u.setLastLoginTime(LocalDateTime.now());
        u.setLastLoginIp(ip);
        try (TenantContext.Scope tenantScope = TenantContext.withTenant(tenantId)) {
            userMapper.updateById(u);
        }
    }
}
