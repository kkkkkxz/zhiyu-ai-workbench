package com.zwskchat.zwskchat.analytics.controller;

import com.zwskchat.zwskchat.analytics.service.AiRiskDashboardService;
import com.zwskchat.zwskchat.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "AI 风险看板")
@RestController
@RequestMapping("/analytics/ai-risk")
@RequiredArgsConstructor
public class AiRiskDashboardController {

    private final AiRiskDashboardService aiRiskDashboardService;

    @Operation(summary = "AI 风险总览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(aiRiskDashboardService.overview());
    }

    @Operation(summary = "客户风险")
    @GetMapping("/customers")
    public Result<Map<String, Object>> customerRisk() {
        return Result.success(aiRiskDashboardService.customerRisk());
    }

    @Operation(summary = "合同回款风险")
    @GetMapping("/contracts")
    public Result<Map<String, Object>> contractRisk() {
        return Result.success(aiRiskDashboardService.contractRisk());
    }

    @Operation(summary = "工单风险")
    @GetMapping("/work-orders")
    public Result<Map<String, Object>> workOrderRisk() {
        return Result.success(aiRiskDashboardService.workOrderRisk());
    }

    @Operation(summary = "派发任务风险")
    @GetMapping("/dispatches")
    public Result<Map<String, Object>> dispatchRisk() {
        return Result.success(aiRiskDashboardService.dispatchRisk());
    }
}
