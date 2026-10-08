package com.zwskchat.zwskchat.ai.hub;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.ai.hub.entity.AiActionExecutionLog;
import com.zwskchat.zwskchat.ai.hub.service.AiActionAuditService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 全业务动作")
@RestController
@RequestMapping("/ai-action")
@RequiredArgsConstructor
public class AiBizActionController {

    private final AiBizActionService bizActionService;
    private final AiActionAuditService actionAuditService;

    @Operation(summary = "AI解析并预览（第一步：识别意图 + 提取信息 + 生成确认）")
    @PostMapping("/preview")
    public Result<BizActionResult> preview(@RequestBody Map<String, String> body) {
        String input = body.get("input");
        if (input == null || input.isBlank()) {
            return Result.fail(1001, "请输入操作指令");
        }
        BizActionResult result = bizActionService.parseAndPreview(input);
        actionAuditService.recordPreview(result.getActionType(), input, result);
        return Result.success(result);
    }

    @Operation(summary = "确认执行（第二步：用户确认后真正执行业务操作）")
    @PostMapping("/confirm")
    public Result<BizActionResult> confirm(@RequestBody Map<String, Object> body) {
        String actionTypeStr = (String) body.get("actionType");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("previewData");
        if (actionTypeStr == null || data == null) {
            return Result.fail(1001, "参数不完整");
        }
        BizActionType actionType;
        try {
            actionType = BizActionType.valueOf(actionTypeStr);
        } catch (IllegalArgumentException e) {
            return Result.fail(1001, "未知操作类型");
        }
        BizActionResult result = bizActionService.confirmExecute(actionType, data);
        actionAuditService.recordExecution(actionType, data, result);
        return Result.success(result);
    }

    @Operation(summary = "我的 AI 动作审计日志")
    @GetMapping("/logs")
    public Result<IPage<AiActionExecutionLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(actionAuditService.listMyLogs(new Page<>(page, size)));
    }
}
