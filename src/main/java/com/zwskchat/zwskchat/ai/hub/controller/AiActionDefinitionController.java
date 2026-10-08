package com.zwskchat.zwskchat.ai.hub.controller;

import com.zwskchat.zwskchat.ai.hub.entity.AiActionDefinition;
import com.zwskchat.zwskchat.ai.hub.service.AiActionDefinitionService;
import com.zwskchat.zwskchat.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "AI 动作定义管理")
@RestController
@RequestMapping("/ai-action/definitions")
@RequiredArgsConstructor
public class AiActionDefinitionController {

    private final AiActionDefinitionService definitionService;

    @Operation(summary = "查询已启用的动作定义列表")
    @GetMapping
    public Result<List<AiActionDefinition>> list() {
        return Result.success(definitionService.listEnabled());
    }

    @Operation(summary = "按编码查询动作定义")
    @GetMapping("/{actionCode}")
    public Result<AiActionDefinition> getByCode(@PathVariable String actionCode) {
        AiActionDefinition def = definitionService.getByCode(actionCode);
        if (def == null) {
            return Result.fail(404, "动作定义不存在: " + actionCode);
        }
        return Result.success(def);
    }

    @Operation(summary = "创建动作定义（租户级）")
    @PostMapping
    public Result<AiActionDefinition> create(@RequestBody AiActionDefinition definition) {
        return Result.success(definitionService.create(definition));
    }

    @Operation(summary = "更新动作定义")
    @PutMapping("/{id}")
    public Result<AiActionDefinition> update(@PathVariable Long id, @RequestBody AiActionDefinition definition) {
        definition.setId(id);
        return Result.success(definitionService.update(definition));
    }

    @Operation(summary = "启用/禁用动作定义")
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        definitionService.toggleEnabled(id, enabled);
        return Result.success(null);
    }
}
