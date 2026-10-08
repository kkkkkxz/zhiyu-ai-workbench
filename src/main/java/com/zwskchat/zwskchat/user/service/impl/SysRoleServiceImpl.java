package com.zwskchat.zwskchat.user.service.impl;

import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysRole;
import com.zwskchat.zwskchat.user.mapper.SysRoleMapper;
import com.zwskchat.zwskchat.user.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper roleMapper;

    @Override
    public Set<String> listRoleCodesByUserId(Long userId) {
        if (userId == null) return Collections.emptySet();
        Long tenantId = TenantContext.getTenantId();
        return listRoleCodesByTenantAndUserId(tenantId, userId);
    }

    @Override
    public Set<String> listRoleCodesByTenantAndUserId(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return Collections.emptySet();
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysRole> roles = roleMapper.selectRolesByTenantAndUserId(tenantId, userId);
            return roles.stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        }
    }
}
