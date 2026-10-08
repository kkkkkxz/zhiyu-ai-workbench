package com.zwskchat.zwskchat.tenant.service;

import com.zwskchat.zwskchat.tenant.dto.TenantFeatureResponse;

import java.util.List;
import java.util.Set;

public interface TenantFeatureService {

    List<TenantFeatureResponse> listCurrentTenantFeatures();

    List<TenantFeatureResponse> listTenantFeatures(Long tenantId);

    TenantFeatureResponse updateFeature(Long tenantId, String featureCode, boolean enabled);

    void ensureDefaultFeatures(Long tenantId);

    void enableTemplateFeatures(Long tenantId, String packageCode);

    Set<String> listCurrentTenantEnabledFeatureCodes();
}
