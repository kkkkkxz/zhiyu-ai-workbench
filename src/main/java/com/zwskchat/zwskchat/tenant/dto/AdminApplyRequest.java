package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminApplyRequest {
    @NotNull
    private Long userId;
    @NotBlank
    private String targetRole;
    private String reason;
}
