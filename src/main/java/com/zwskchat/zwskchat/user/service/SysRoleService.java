package com.zwskchat.zwskchat.user.service;

import java.util.Set;

public interface SysRoleService {
    Set<String> listRoleCodesByUserId(Long userId);
    Set<String> listRoleCodesByTenantAndUserId(Long tenantId, Long userId);
}
