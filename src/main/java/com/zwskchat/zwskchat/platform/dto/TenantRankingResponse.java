package com.zwskchat.zwskchat.platform.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantRankingResponse {
    private Long tenantId;
    private String tenantName;
    private String tenantCode;
    private Integer status;
    private long userTotal;
    private long chatTotal;
    private long kbTotal;
    private long documentTotal;
    private long tokenTotal;
    private LocalDateTime lastActiveTime;
}
