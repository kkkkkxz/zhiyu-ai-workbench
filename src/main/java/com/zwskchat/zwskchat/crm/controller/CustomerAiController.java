package com.zwskchat.zwskchat.crm.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizCustomerScoreLog;
import com.zwskchat.zwskchat.crm.service.CustomerAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "CRM 客户 AI")
@RestController
@RequestMapping("/crm/ai")
@RequiredArgsConstructor
public class CustomerAiController {

    private final CustomerAiService customerAiService;

    @Operation(summary = "AI 客户评分")
    @PostMapping("/customers/{customerId}/score")
    public Result<BizCustomer> scoreCustomer(@PathVariable Long customerId) {
        return Result.success(customerAiService.scoreCustomer(customerId));
    }

    @Operation(summary = "批量 AI 客户评分")
    @PostMapping("/customers/batch-score")
    public Result<List<BizCustomer>> batchScore() {
        return Result.success(customerAiService.batchScore());
    }

    @Operation(summary = "客户评分历史")
    @GetMapping("/customers/{customerId}/score-history")
    public Result<List<BizCustomerScoreLog>> scoreHistory(@PathVariable Long customerId) {
        return Result.success(customerAiService.scoreHistory(customerId));
    }

    @Operation(summary = "客户生命周期汇总")
    @GetMapping("/customers/lifecycle-summary")
    public Result<Map<String, Object>> lifecycleSummary() {
        return Result.success(customerAiService.lifecycleSummary());
    }
}
