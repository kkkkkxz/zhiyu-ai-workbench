package com.zwskchat.zwskchat.tenant.service;

import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantUserRequest;
import com.zwskchat.zwskchat.tenant.dto.RoleResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantBossStatResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantUserResponse;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantUserRequest;

import java.util.List;

public interface TenantManagementService {

    PageResult<TenantResponse> listTenants(long page, long size, String keyword, Integer status);

    TenantResponse createTenant(CreateTenantRequest request);

    TenantResponse updateTenant(Long tenantId, UpdateTenantRequest request);

    void deleteTenant(Long tenantId);

    PageResult<TenantUserResponse> listUsers(Long tenantId, long page, long size, String keyword, Integer status);

    TenantUserResponse createUser(Long tenantId, CreateTenantUserRequest request);

    TenantUserResponse updateUser(Long tenantId, Long userId, UpdateTenantUserRequest request);

    void deleteUser(Long tenantId, Long userId);

    List<RoleResponse> listRoles(Long tenantId);

    List<TenantBossStatResponse> listTenantBossStats();
}
