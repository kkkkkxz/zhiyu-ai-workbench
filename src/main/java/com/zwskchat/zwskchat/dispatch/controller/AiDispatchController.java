package com.zwskchat.zwskchat.dispatch.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.service.AiDispatchService;
import com.zwskchat.zwskchat.dispatch.service.AiDispatchService.DispatchPreview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 智能派发")
@RestController
@RequestMapping("/ai-dispatch")
@RequiredArgsConstructor
public class AiDispatchController {

    private final AiDispatchService aiDispatchService;

    @Operation(summary = "解析并预览（第一步：AI解析意图 + 层级校验）")
    @PostMapping("/preview")
    public Result<DispatchPreview> preview(@RequestBody Map<String, String> body) {
        String input = body.get("input");
        if (input == null || input.isBlank()) {
            return Result.fail(1001, "请输入派发内容");
        }
        return Result.success(aiDispatchService.parseAndPreview(input));
    }

    @Operation(summary = "确认派发（第二步：执行派发 + 同步日程 + 发送通知）")
    @PostMapping("/confirm")
    public Result<BizDispatch> confirm(@RequestBody DispatchPreview preview) {
        return Result.success(aiDispatchService.confirmAndDispatch(preview));
    }
}
