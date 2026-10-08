package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTenantAiConfigRequest {

    @Size(max = 50, message = "模型供应商不能超过50个字符")
    private String provider;

    @Size(max = 300, message = "模型地址不能超过300个字符")
    private String baseUrl;

    @Size(max = 300, message = "API Key 不能超过300个字符")
    private String apiKey;

    @Size(max = 100, message = "对话模型不能超过100个字符")
    private String chatModel;

    @Size(max = 100, message = "向量模型不能超过100个字符")
    private String embeddingModel;

    @Size(max = 100, message = "视觉模型不能超过100个字符")
    private String visionModel;

    @DecimalMin(value = "0.0", message = "温度不能小于0")
    @DecimalMax(value = "2.0", message = "温度不能大于2")
    private Double temperature;

    private Boolean enabled;

    @Size(max = 300, message = "备注不能超过300个字符")
    private String remark;
}
