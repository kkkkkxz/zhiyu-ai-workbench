package com.zwskchat.zwskchat.platform.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformOverviewResponse {
    private long tenantTotal;
    private long activeTenantTotal;
    private long userTotal;
    private long todayActiveUserTotal;
    private long todayChatTotal;
    private long kbTotal;
    private long documentTotal;
    private long departmentTotal;
    private long pendingAdminApplyTotal;
    private long todayTokenTotal;
    private long todayErrorTotal;
}
