package com.zwskchat.zwskchat.user.service.impl;

import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysPermission;
import com.zwskchat.zwskchat.user.mapper.SysPermissionMapper;
import com.zwskchat.zwskchat.user.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysPermissionServiceImpl implements SysPermissionService {

    private final SysPermissionMapper permMapper;

    @Override
    public Set<String> listPermCodesByUserId(Long userId) {
        if (userId == null) return Collections.emptySet();
        Long tenantId = TenantContext.getTenantId();
        return listPermCodesByTenantAndUserId(tenantId, userId);
    }

    @Override
    public Set<String> listPermCodesByTenantAndUserId(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) return Collections.emptySet();
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<SysPermission> list = permMapper.selectPermissionsByTenantAndUserId(tenantId, userId);
            return list.stream().map(SysPermission::getPermCode).collect(Collectors.toSet());
        }
    }
}
