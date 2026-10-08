package com.zwskchat.zwskchat.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.workflow.entity.WfDefinition;
import com.zwskchat.zwskchat.workflow.entity.WfInstance;
import com.zwskchat.zwskchat.workflow.entity.WfTask;
import com.zwskchat.zwskchat.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "审批流管理")
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "流程定义列表")
    @GetMapping("/definitions")
    public Result<List<WfDefinition>> listDefinitions() {
        return Result.success(workflowService.listDefinitions());
    }

    @Operation(summary = "创建流程定义")
    @PostMapping("/definitions")
    public Result<WfDefinition> createDefinition(@RequestBody WfDefinition def) {
        return Result.success(workflowService.createDefinition(def));
    }

    @Operation(summary = "发起审批")
    @PostMapping("/instances")
    public Result<WfInstance> startInstance(@RequestBody Map<String, Object> body) {
        Long definitionId = Long.valueOf(body.get("definitionId").toString());
        String bizType = (String) body.get("bizType");
        Long bizId = Long.valueOf(body.get("bizId").toString());
        String title = (String) body.get("title");
        String formData = body.containsKey("formData") ? body.get("formData").toString() : null;
        return Result.success(workflowService.startInstance(definitionId, bizType, bizId, title, formData));
    }

    @Operation(summary = "审批实例详情")
    @GetMapping("/instances/{id}")
    public Result<WfInstance> getInstance(@PathVariable Long id) {
        return Result.success(workflowService.getInstance(id));
    }

    @Operation(summary = "我发起的")
    @GetMapping("/my-initiated")
    public Result<IPage<WfInstance>> myInitiated(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(workflowService.myInitiated(new Page<>(page, size)));
    }

    @Operation(summary = "我的待办")
    @GetMapping("/my-tasks")
    public Result<IPage<WfTask>> myTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(workflowService.myPendingTasks(new Page<>(page, size)));
    }

    @Operation(summary = "审批通过")
    @PostMapping("/tasks/{taskId}/approve")
    public Result<Void> approve(@PathVariable Long taskId, @RequestBody(required = false) Map<String, String> body) {
        workflowService.approve(taskId, body != null ? body.get("comment") : null);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PostMapping("/tasks/{taskId}/reject")
    public Result<Void> reject(@PathVariable Long taskId, @RequestBody(required = false) Map<String, String> body) {
        workflowService.reject(taskId, body != null ? body.get("comment") : null);
        return Result.success();
    }
}
