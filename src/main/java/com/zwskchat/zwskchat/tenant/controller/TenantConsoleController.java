package com.zwskchat.zwskchat.tenant.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.tenant.dto.TenantAiConfigResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantFeatureResponse;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantAiConfigRequest;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantFeatureRequest;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.tenant.service.TenantFeatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tenant-console")
@PreAuthorize("isAuthenticated()")
public class TenantConsoleController {

    private final TenantFeatureService tenantFeatureService;
    private final TenantAiConfigService tenantAiConfigService;

    @GetMapping("/features")
    public Result<List<TenantFeatureResponse>> listCurrentTenantFeatures() {
        return Result.success(tenantFeatureService.listCurrentTenantFeatures());
    }

    @GetMapping("/tenants/{tenantId}/features")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public Result<List<TenantFeatureResponse>> listTenantFeatures(@PathVariable Long tenantId) {
        return Result.success(tenantFeatureService.listTenantFeatures(tenantId));
    }

    @PutMapping("/tenants/{tenantId}/features/{featureCode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public Result<TenantFeatureResponse> updateTenantFeature(@PathVariable Long tenantId,
                                                             @PathVariable String featureCode,
                                                             @Valid @RequestBody UpdateTenantFeatureRequest request) {
        return Result.success(tenantFeatureService.updateFeature(
                tenantId, featureCode, Boolean.TRUE.equals(request.getEnabled())));
    }

    @GetMapping("/tenants/{tenantId}/ai-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public Result<TenantAiConfigResponse> getTenantAiConfig(@PathVariable Long tenantId) {
        return Result.success(tenantAiConfigService.getConfig(tenantId));
    }

    @PutMapping("/tenants/{tenantId}/ai-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public Result<TenantAiConfigResponse> updateTenantAiConfig(@PathVariable Long tenantId,
                                                               @Valid @RequestBody UpdateTenantAiConfigRequest request) {
        return Result.success(tenantAiConfigService.updateConfig(tenantId, request));
    }
}
