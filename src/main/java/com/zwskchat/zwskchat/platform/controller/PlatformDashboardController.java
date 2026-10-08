package com.zwskchat.zwskchat.platform.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.platform.dto.PlatformOverviewResponse;
import com.zwskchat.zwskchat.platform.dto.TenantRankingResponse;
import com.zwskchat.zwskchat.platform.service.PlatformDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "平台数据看板")
@RestController
@RequestMapping("/platform/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformDashboardController {

    private final PlatformDashboardService dashboardService;

    @Operation(summary = "平台总览统计")
    @GetMapping("/overview")
    public Result<PlatformOverviewResponse> overview() {
        return Result.success(dashboardService.getOverview());
    }

    @Operation(summary = "租户排名")
    @GetMapping("/tenant-ranking")
    public Result<List<TenantRankingResponse>> tenantRanking(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(dashboardService.getTenantRanking(limit));
    }
}
