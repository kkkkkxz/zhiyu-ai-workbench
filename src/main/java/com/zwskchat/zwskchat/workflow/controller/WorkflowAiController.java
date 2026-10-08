package com.zwskchat.zwskchat.workflow.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.workflow.service.WorkflowAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "审批 AI")
@RestController
@RequestMapping("/workflow/ai")
@RequiredArgsConstructor
public class WorkflowAiController {

    private final WorkflowAiService workflowAiService;

    @Operation(summary = "AI 审批建议")
    @GetMapping("/tasks/{taskId}/suggestion")
    public Result<Map<String, Object>> approvalSuggestion(@PathVariable Long taskId) {
        return Result.success(workflowAiService.approvalSuggestion(taskId));
    }

    @Operation(summary = "审批瓶颈分析")
    @GetMapping("/bottleneck-report")
    public Result<Map<String, Object>> bottleneckReport() {
        return Result.success(workflowAiService.bottleneckReport());
    }
}
