package com.zwskchat.zwskchat.tenant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantBossStatResponse {
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private Integer status;
    private Long superAdminCount;
}
