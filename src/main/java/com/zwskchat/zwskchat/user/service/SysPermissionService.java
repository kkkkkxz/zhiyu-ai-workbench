package com.zwskchat.zwskchat.user.service;

import java.util.Set;

public interface SysPermissionService {
    Set<String> listPermCodesByUserId(Long userId);
    Set<String> listPermCodesByTenantAndUserId(Long tenantId, Long userId);
}
