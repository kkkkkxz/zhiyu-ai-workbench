package com.zwskchat.zwskchat.tenant.controller;

import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.CreateTenantUserRequest;
import com.zwskchat.zwskchat.tenant.dto.RoleResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantBossStatResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantUserResponse;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantRequest;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantUserRequest;
import com.zwskchat.zwskchat.tenant.service.TenantManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/tenants")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class TenantManagementController {

    private final TenantManagementService tenantManagementService;

    @GetMapping("/boss-stats")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Result<List<TenantBossStatResponse>> listTenantBossStats() {
        return Result.success(tenantManagementService.listTenantBossStats());
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<PageResult<TenantResponse>> listTenants(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(tenantManagementService.listTenants(page, size, keyword, status));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return Result.success(tenantManagementService.createTenant(request));
    }

    @PutMapping("/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<TenantResponse> updateTenant(@PathVariable Long tenantId,
                                               @Valid @RequestBody UpdateTenantRequest request) {
        return Result.success(tenantManagementService.updateTenant(tenantId, request));
    }

    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> deleteTenant(@PathVariable Long tenantId) {
        tenantManagementService.deleteTenant(tenantId);
        return Result.success();
    }

    @GetMapping("/{tenantId}/users")
    public Result<PageResult<TenantUserResponse>> listUsers(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(tenantManagementService.listUsers(tenantId, page, size, keyword, status));
    }

    @PostMapping("/{tenantId}/users")
    public Result<TenantUserResponse> createUser(@PathVariable Long tenantId,
                                                 @Valid @RequestBody CreateTenantUserRequest request) {
        return Result.success(tenantManagementService.createUser(tenantId, request));
    }

    @PutMapping("/{tenantId}/users/{userId}")
    public Result<TenantUserResponse> updateUser(@PathVariable Long tenantId,
                                                 @PathVariable Long userId,
                                                 @Valid @RequestBody UpdateTenantUserRequest request) {
        return Result.success(tenantManagementService.updateUser(tenantId, userId, request));
    }

    @DeleteMapping("/{tenantId}/users/{userId}")
    public Result<Void> deleteUser(@PathVariable Long tenantId, @PathVariable Long userId) {
        tenantManagementService.deleteUser(tenantId, userId);
        return Result.success();
    }

    @GetMapping("/{tenantId}/roles")
    public Result<List<RoleResponse>> listRoles(@PathVariable Long tenantId) {
        return Result.success(tenantManagementService.listRoles(tenantId));
    }
}
