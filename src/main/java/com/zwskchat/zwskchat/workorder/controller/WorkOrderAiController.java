package com.zwskchat.zwskchat.workorder.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.service.WorkOrderAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "工单 AI")
@RestController
@RequestMapping("/work-orders/ai")
@RequiredArgsConstructor
public class WorkOrderAiController {

    private final WorkOrderAiService workOrderAiService;

    @Operation(summary = "AI 分析工单并生成处理建议")
    @PostMapping("/{workOrderId}/analyze")
    public Result<BizWorkOrder> analyze(@PathVariable Long workOrderId) {
        return Result.success(workOrderAiService.analyzeAndSuggest(workOrderId));
    }

    @Operation(summary = "AI 智能分配工单")
    @PostMapping("/{workOrderId}/smart-assign")
    public Result<BizWorkOrder> smartAssign(@PathVariable Long workOrderId) {
        return Result.success(workOrderAiService.smartAssign(workOrderId));
    }

    @Operation(summary = "工单进度与超时报告")
    @GetMapping("/progress-report")
    public Result<Map<String, Object>> progressReport() {
        return Result.success(workOrderAiService.progressReport());
    }
}
