package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTenantFeatureRequest {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}
