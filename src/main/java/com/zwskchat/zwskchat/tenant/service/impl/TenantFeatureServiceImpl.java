package com.zwskchat.zwskchat.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.dto.TenantFeatureResponse;
import com.zwskchat.zwskchat.tenant.entity.TenantFeatureConfig;
import com.zwskchat.zwskchat.tenant.mapper.TenantFeatureConfigMapper;
import com.zwskchat.zwskchat.tenant.model.TenantFeatureCatalog;
import com.zwskchat.zwskchat.tenant.model.TenantFeatureDefinition;
import com.zwskchat.zwskchat.tenant.service.TenantFeatureService;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantFeatureServiceImpl implements TenantFeatureService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;

    private final TenantFeatureConfigMapper featureConfigMapper;
    private final SysTenantMapper tenantMapper;

    @Override
    public List<TenantFeatureResponse> listCurrentTenantFeatures() {
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        ensureDefaultFeatures(tenantId);
        Map<String, TenantFeatureConfig> configs = loadConfigs(tenantId);
        return TenantFeatureCatalog.definitions().stream()
                .map(definition -> TenantFeatureResponse.from(definition, configs.get(definition.code())))
                .toList();
    }

    @Override
    public List<TenantFeatureResponse> listTenantFeatures(Long tenantId) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        ensureDefaultFeatures(scopedTenantId);
        Map<String, TenantFeatureConfig> configs = loadConfigs(scopedTenantId);
        return TenantFeatureCatalog.definitions().stream()
                .map(definition -> TenantFeatureResponse.from(definition, configs.get(definition.code())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantFeatureResponse updateFeature(Long tenantId, String featureCode, boolean enabled) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        TenantFeatureDefinition definition = requireDefinition(featureCode);
        TenantFeatureConfig config = upsertFeature(scopedTenantId, definition, enabled, null, false);
        return TenantFeatureResponse.from(definition, config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaultFeatures(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "租户ID不能为空");
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            requireTenant(tenantId);
            Map<String, TenantFeatureConfig> configs = loadConfigs(tenantId);
            for (TenantFeatureDefinition definition : TenantFeatureCatalog.definitions()) {
                if (!configs.containsKey(definition.code())) {
                    upsertFeature(tenantId, definition, definition.enabledByDefault(), null, true);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableTemplateFeatures(Long tenantId, String packageCode) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "租户ID不能为空");
        }
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            requireTenant(tenantId);
            ensureDefaultFeatures(tenantId);
            Map<String, TenantFeatureDefinition> definitions = TenantFeatureCatalog.definitions().stream()
                    .collect(Collectors.toMap(TenantFeatureDefinition::code, Function.identity()));
            for (String featureCode : TenantFeatureCatalog.featureCodesForTemplate(packageCode)) {
                TenantFeatureDefinition definition = definitions.get(featureCode);
                if (definition != null) {
                    upsertFeature(tenantId, definition, true, packageCode, false);
                }
            }
        }
    }

    @Override
    public Set<String> listCurrentTenantEnabledFeatureCodes() {
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        ensureDefaultFeatures(tenantId);
        return listTenantFeatures(tenantId).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(TenantFeatureResponse::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, TenantFeatureConfig> loadConfigs(Long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            List<TenantFeatureConfig> configs = featureConfigMapper.selectList(
                    Wrappers.<TenantFeatureConfig>lambdaQuery()
                            .eq(TenantFeatureConfig::getTenantId, tenantId)
                            .orderByAsc(TenantFeatureConfig::getSort)
                            .orderByAsc(TenantFeatureConfig::getId));
            Map<String, TenantFeatureConfig> result = new LinkedHashMap<>(configs.size());
            for (TenantFeatureConfig config : configs) {
                result.put(config.getFeatureCode(), config);
            }
            return result;
        }
    }

    private TenantFeatureConfig upsertFeature(Long tenantId,
                                              TenantFeatureDefinition definition,
                                              boolean enabled,
                                              String sourceTemplateCode,
                                              boolean createOnly) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            TenantFeatureConfig existing = featureConfigMapper.selectOne(Wrappers.<TenantFeatureConfig>lambdaQuery()
                    .eq(TenantFeatureConfig::getTenantId, tenantId)
                    .eq(TenantFeatureConfig::getFeatureCode, definition.code())
                    .last("LIMIT 1"));
            if (existing == null) {
                TenantFeatureConfig config = new TenantFeatureConfig();
                config.setTenantId(tenantId);
                config.setFeatureCode(definition.code());
                config.setFeatureName(definition.name());
                config.setCategory(definition.category());
                config.setDescription(definition.description());
                config.setSourceTemplateCode(sourceTemplateCode);
                config.setEnabled(enabled ? ENABLED : DISABLED);
                config.setConfigJson("{}");
                config.setSort(definition.sort());
                featureConfigMapper.insert(config);
                return config;
            }
            if (createOnly) {
                return existing;
            }
            existing.setFeatureName(definition.name());
            existing.setCategory(definition.category());
            existing.setDescription(definition.description());
            if (sourceTemplateCode != null && !sourceTemplateCode.isBlank()) {
                existing.setSourceTemplateCode(sourceTemplateCode);
            }
            existing.setEnabled(enabled ? ENABLED : DISABLED);
            existing.setSort(definition.sort());
            featureConfigMapper.updateById(existing);
            return featureConfigMapper.selectById(existing.getId());
        }
    }

    private TenantFeatureDefinition requireDefinition(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "功能编码不能为空");
        }
        return TenantFeatureCatalog.definitions().stream()
                .filter(definition -> Objects.equals(definition.code(), featureCode.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "功能不存在: " + featureCode));
    }

    private Long resolveManageableTenantId(Long requestedTenantId) {
        LoginUser currentUser = SecurityUtils.currentUser();
        Set<String> roles = currentUser.getRoles();
        if (roles != null && roles.contains(ROLE_SUPER_ADMIN)) {
            Long tenantId = requestedTenantId == null ? currentUser.getTenantId() : requestedTenantId;
            try (TenantContext.Scope ignored = TenantContext.ignore()) {
                requireTenant(tenantId);
            }
            return tenantId;
        }
        if (roles == null || !roles.contains(ROLE_TENANT_ADMIN)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        Long currentTenantId = currentUser.getTenantId();
        if (requestedTenantId != null && !currentTenantId.equals(requestedTenantId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能管理其他租户");
        }
        return currentTenantId;
    }

    private void requireTenant(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "租户ID不能为空");
        }
        SysTenant tenant = tenantMapper.selectExistingById(tenantId);
        if (tenant == null) {
            throw new BusinessException(ResultCode.TENANT_NOT_FOUND);
        }
    }
}
