package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.tenant.entity.TenantAiConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantAiConfigResponse {

    private String provider;
    private String baseUrl;
    private String apiKeyMask;
    private Boolean apiKeyConfigured;
    private String chatModel;
    private String embeddingModel;
    private String visionModel;
    private Double temperature;
    private Boolean enabled;
    private String remark;
    private LocalDateTime updateTime;

    public static TenantAiConfigResponse from(TenantAiConfig config) {
        if (config == null) {
            return TenantAiConfigResponse.builder()
                    .provider("dashscope-sdk")
                    .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    .chatModel("qwen-plus")
                    .embeddingModel("text-embedding-v3")
                    .visionModel("qwen-vl-plus")
                    .temperature(0.3D)
                    .enabled(false)
                    .apiKeyConfigured(false)
                    .build();
        }
        return TenantAiConfigResponse.builder()
                .provider(config.getProvider())
                .baseUrl(config.getBaseUrl())
                .apiKeyMask(config.getApiKeyMask())
                .apiKeyConfigured(config.getApiKeyEnc() != null && !config.getApiKeyEnc().isBlank())
                .chatModel(config.getChatModel())
                .embeddingModel(config.getEmbeddingModel())
                .visionModel(config.getVisionModel())
                .temperature(config.getTemperature())
                .enabled(Integer.valueOf(1).equals(config.getEnabled()))
                .remark(config.getRemark())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
