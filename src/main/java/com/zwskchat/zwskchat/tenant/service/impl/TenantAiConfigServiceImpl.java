package com.zwskchat.zwskchat.tenant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.crypto.SecretCryptoService;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.tenant.dto.TenantAiConfigResponse;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.dto.UpdateTenantAiConfigRequest;
import com.zwskchat.zwskchat.tenant.entity.TenantAiConfig;
import com.zwskchat.zwskchat.tenant.mapper.TenantAiConfigMapper;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantAiConfigServiceImpl implements TenantAiConfigService {

    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";
    private static final int ENABLED = 1;
    private static final double MAX_CHAT_TEMPERATURE = 0.15D;

    private final TenantAiConfigMapper tenantAiConfigMapper;
    private final SysTenantMapper tenantMapper;
    private final SecretCryptoService secretCryptoService;
    private final AiProperties aiProperties;

    @Override
    public TenantAiConfigResponse getConfig(Long tenantId) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        return TenantAiConfigResponse.from(loadConfig(scopedTenantId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantAiConfigResponse updateConfig(Long tenantId, UpdateTenantAiConfigRequest request) {
        Long scopedTenantId = resolveManageableTenantId(tenantId);
        TenantAiConfig config = loadConfig(scopedTenantId);
        if (config == null) {
            config = new TenantAiConfig();
            config.setTenantId(scopedTenantId);
            config.setProvider(defaultString(request.getProvider(), aiProperties.getChat().getProvider()));
            String defaultBaseUrl = resolveDefaultBaseUrl(config.getProvider());
            config.setBaseUrl(defaultString(request.getBaseUrl(), defaultBaseUrl));
            config.setChatModel(defaultString(request.getChatModel(), aiProperties.getChat().getModel()));
            config.setEmbeddingModel(defaultString(request.getEmbeddingModel(), aiProperties.getEmbedding().getModel()));
            config.setVisionModel(defaultString(request.getVisionModel(), aiProperties.getDashscope().getVisionModel()));
            config.setTemperature(normalizeTemperature(defaultDouble(request.getTemperature(), aiProperties.getChat().getTemperature())));
            config.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : ENABLED);
        } else {
            applyNullableValues(config, request);
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            String apiKey = request.getApiKey().trim();
            config.setApiKeyEnc(secretCryptoService.encrypt(apiKey));
            config.setApiKeyMask(maskApiKey(apiKey));
        }
        if (request.getRemark() != null) {
            config.setRemark(blankToNull(request.getRemark()));
        }
        if (config.getId() == null) {
            insertConfig(config);
        } else {
            updateExistingConfig(config);
        }
        return TenantAiConfigResponse.from(loadConfig(scopedTenantId));
    }

    @Override
    public Optional<TenantAiRuntimeConfig> getCurrentRuntimeConfig() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = SecurityUtils.currentTenantIdOrNull();
        }
        if (tenantId == null) {
            return Optional.empty();
        }
        TenantAiConfig config = loadConfig(tenantId);
        if (config == null || !Integer.valueOf(ENABLED).equals(config.getEnabled())
                || config.getApiKeyEnc() == null || config.getApiKeyEnc().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(TenantAiRuntimeConfig.builder()
                .tenantId(tenantId)
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKey(secretCryptoService.decrypt(config.getApiKeyEnc()))
                .chatModel(config.getChatModel())
                .embeddingModel(config.getEmbeddingModel())
                .visionModel(config.getVisionModel())
                .temperature(normalizeTemperature(config.getTemperature()))
                .build());
    }

    private void applyNullableValues(TenantAiConfig config, UpdateTenantAiConfigRequest request) {
        if (request.getProvider() != null) {
            config.setProvider(defaultString(request.getProvider(), aiProperties.getChat().getProvider()));
        }
        if (request.getBaseUrl() != null) {
            config.setBaseUrl(defaultString(request.getBaseUrl(), resolveDefaultBaseUrl(config.getProvider())));
        }
        if (request.getChatModel() != null) {
            config.setChatModel(defaultString(request.getChatModel(), aiProperties.getChat().getModel()));
        }
        if (request.getEmbeddingModel() != null) {
            config.setEmbeddingModel(defaultString(request.getEmbeddingModel(), aiProperties.getEmbedding().getModel()));
        }
        if (request.getVisionModel() != null) {
            config.setVisionModel(defaultString(request.getVisionModel(), aiProperties.getDashscope().getVisionModel()));
        }
        if (request.getTemperature() != null) {
            config.setTemperature(normalizeTemperature(request.getTemperature()));
        }
        if (request.getEnabled() != null) {
            config.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? ENABLED : 0);
        }
    }

    private TenantAiConfig loadConfig(Long tenantId) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            return tenantAiConfigMapper.selectOne(Wrappers.<TenantAiConfig>lambdaQuery()
                    .eq(TenantAiConfig::getTenantId, tenantId)
                    .last("LIMIT 1"));
        }
    }

    private void insertConfig(TenantAiConfig config) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            tenantAiConfigMapper.insert(config);
        }
    }

    private void updateExistingConfig(TenantAiConfig config) {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            tenantAiConfigMapper.updateById(config);
        }
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

    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 6) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private String resolveDefaultBaseUrl(String provider) {
        if ("openai-compat".equals(provider)) {
            return aiProperties.getChat().getBaseUrl();
        }
        return aiProperties.getDashscope().getCompatibleBaseUrl();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private Double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Double normalizeTemperature(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return aiProperties.getChat().getTemperature();
        }
        return Math.max(0D, Math.min(value, MAX_CHAT_TEMPERATURE));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
