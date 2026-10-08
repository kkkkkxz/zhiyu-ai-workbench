package com.zwskchat.zwskchat.dispatch.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.dispatch.dto.DispatchDeptOption;
import com.zwskchat.zwskchat.dispatch.dto.DispatchUserOption;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatchAssignee;
import com.zwskchat.zwskchat.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "工作派发")
@RestController
@RequestMapping("/dispatch")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @Operation(summary = "我派发的")
    @GetMapping("/sent")
    public Result<IPage<BizDispatch>> mySent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dispatchService.mySent(new Page<>(page, size)));
    }

    @Operation(summary = "我收到的")
    @GetMapping("/received")
    public Result<IPage<BizDispatch>> myReceived(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dispatchService.myReceived(new Page<>(page, size)));
    }

    @Operation(summary = "可指派部门")
    @GetMapping("/assignable-departments")
    public Result<List<DispatchDeptOption>> assignableDepartments() {
        return Result.success(dispatchService.assignableDepartments());
    }

    @Operation(summary = "可指派人员")
    @GetMapping("/assignable-users")
    public Result<List<DispatchUserOption>> assignableUsers(@RequestParam(required = false) Long deptId) {
        return Result.success(dispatchService.assignableUsers(deptId));
    }

    @Operation(summary = "派发详情")
    @GetMapping("/{id}")
    public Result<BizDispatch> get(@PathVariable Long id) {
        return Result.success(dispatchService.getById(id));
    }

    @Operation(summary = "创建派发")
    @PostMapping
    public Result<BizDispatch> create(@RequestBody Map<String, Object> body) {
        BizDispatch dispatch = new BizDispatch();
        dispatch.setTitle((String) body.get("title"));
        dispatch.setDescription((String) body.get("description"));
        dispatch.setType((String) body.getOrDefault("type", "task"));
        dispatch.setPriority((String) body.getOrDefault("priority", "medium"));
        @SuppressWarnings("unchecked")
        List<Long> assigneeIds = body.containsKey("assigneeIds")
                ? ((List<Object>) body.get("assigneeIds")).stream().map(v -> Long.parseLong(String.valueOf(v))).toList()
                : List.of();
        return Result.success(dispatchService.create(dispatch, assigneeIds));
    }

    @Operation(summary = "更新进度")
    @PutMapping("/{dispatchId}/progress")
    public Result<Void> updateProgress(
            @PathVariable Long dispatchId,
            @RequestParam Long assigneeId,
            @RequestParam int progress,
            @RequestParam(required = false) String note) {
        dispatchService.updateProgress(dispatchId, assigneeId, progress, note);
        return Result.success();
    }

    @Operation(summary = "完成派发")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam(required = false) String note) {
        dispatchService.complete(id, note);
        return Result.success();
    }

    @Operation(summary = "派发指派人列表")
    @GetMapping("/{dispatchId}/assignees")
    public Result<List<BizDispatchAssignee>> listAssignees(@PathVariable Long dispatchId) {
        return Result.success(dispatchService.listAssignees(dispatchId));
    }
}
