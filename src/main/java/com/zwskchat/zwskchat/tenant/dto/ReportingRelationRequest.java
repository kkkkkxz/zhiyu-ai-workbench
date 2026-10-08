package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportingRelationRequest {
    @NotNull
    private Long userId;
    @NotNull
    private Long managerUserId;
    private Long deptId;
}
