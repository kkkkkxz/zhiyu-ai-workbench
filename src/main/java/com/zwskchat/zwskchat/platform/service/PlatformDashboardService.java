package com.zwskchat.zwskchat.platform.service;

import com.zwskchat.zwskchat.platform.dto.PlatformOverviewResponse;
import com.zwskchat.zwskchat.platform.dto.TenantRankingResponse;

import java.util.List;

public interface PlatformDashboardService {

    PlatformOverviewResponse getOverview();

    List<TenantRankingResponse> getTenantRanking(int limit);
}
