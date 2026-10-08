package com.zwskchat.zwskchat.contract.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizPaymentReminder;
import com.zwskchat.zwskchat.contract.service.ContractAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "合同回款 AI")
@RestController
@RequestMapping("/contracts/ai")
@RequiredArgsConstructor
public class ContractAiController {

    private final ContractAiService contractAiService;

    @Operation(summary = "AI 合同风险分析")
    @PostMapping("/{contractId}/risk-analysis")
    public Result<BizContract> analyzeRisk(@PathVariable Long contractId) {
        return Result.success(contractAiService.analyzeRisk(contractId));
    }

    @Operation(summary = "生成回款提醒")
    @PostMapping("/payment-reminders/generate")
    public Result<List<BizPaymentReminder>> generatePaymentReminders(
            @RequestParam(defaultValue = "7") int daysAhead) {
        return Result.success(contractAiService.generatePaymentReminders(daysAhead));
    }

    @Operation(summary = "待处理回款提醒")
    @GetMapping("/payment-reminders")
    public Result<List<BizPaymentReminder>> pendingPaymentReminders() {
        return Result.success(contractAiService.pendingPaymentReminders());
    }

    @Operation(summary = "回款风险汇总")
    @GetMapping("/payment-risk-summary")
    public Result<Map<String, Object>> paymentRiskSummary() {
        return Result.success(contractAiService.paymentRiskSummary());
    }
}
