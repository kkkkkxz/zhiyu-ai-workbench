package com.zwskchat.zwskchat.tenant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantAiRuntimeConfig {

    private Long tenantId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String chatModel;
    private String embeddingModel;
    private String visionModel;
    private Double temperature;
}
