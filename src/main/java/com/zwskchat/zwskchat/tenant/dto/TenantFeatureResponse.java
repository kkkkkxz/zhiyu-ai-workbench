package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.tenant.entity.TenantFeatureConfig;
import com.zwskchat.zwskchat.tenant.model.TenantFeatureDefinition;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantFeatureResponse {

    private String code;
    private String name;
    private String category;
    private String description;
    private Boolean enabled;
    private Boolean enabledByDefault;
    private String sourceTemplateCode;
    private String configJson;
    private Integer sort;

    public static TenantFeatureResponse from(TenantFeatureDefinition definition, TenantFeatureConfig config) {
        boolean enabled = config == null ? definition.enabledByDefault()
                : Integer.valueOf(1).equals(config.getEnabled());
        return TenantFeatureResponse.builder()
                .code(definition.code())
                .name(definition.name())
                .category(definition.category())
                .description(definition.description())
                .enabled(enabled)
                .enabledByDefault(definition.enabledByDefault())
                .sourceTemplateCode(config == null ? null : config.getSourceTemplateCode())
                .configJson(config == null ? null : config.getConfigJson())
                .sort(definition.sort())
                .build();
    }
}
