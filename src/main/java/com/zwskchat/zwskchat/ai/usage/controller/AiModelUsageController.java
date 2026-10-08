package com.zwskchat.zwskchat.ai.usage.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.ai.usage.entity.AiModelUsageLog;
import com.zwskchat.zwskchat.ai.usage.service.AiModelUsageService;
import com.zwskchat.zwskchat.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 模型用量统计")
@RestController
@RequestMapping("/ai-model-usage")
@RequiredArgsConstructor
public class AiModelUsageController {

    private final AiModelUsageService usageService;

    @Operation(summary = "查询租户用量日志")
    @GetMapping("/logs")
    public Result<IPage<AiModelUsageLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(usageService.listByTenant(new Page<>(page, size)));
    }

    @Operation(summary = "租户今日用量汇总")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(usageService.tenantSummary());
    }
}
