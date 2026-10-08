package com.zwskchat.zwskchat.tenant.service;

import com.zwskchat.zwskchat.tenant.dto.TenantAiConfigResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantAiConfigRequest;

import java.util.Optional;

public interface TenantAiConfigService {

    TenantAiConfigResponse getConfig(Long tenantId);

    TenantAiConfigResponse updateConfig(Long tenantId, UpdateTenantAiConfigRequest request);

    Optional<TenantAiRuntimeConfig> getCurrentRuntimeConfig();
}
