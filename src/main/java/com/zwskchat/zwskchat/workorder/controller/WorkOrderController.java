package com.zwskchat.zwskchat.workorder.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrderComment;
import com.zwskchat.zwskchat.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "工单管理")
@RestController
@RequestMapping("/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @Operation(summary = "工单列表")
    @GetMapping
    public Result<IPage<BizWorkOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword) {
        return Result.success(workOrderService.list(new Page<>(page, size), status, priority, keyword));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<BizWorkOrder> get(@PathVariable Long id) {
        return Result.success(workOrderService.getById(id));
    }

    @Operation(summary = "创建工单")
    @PostMapping
    public Result<BizWorkOrder> create(@RequestBody BizWorkOrder order) {
        return Result.success(workOrderService.create(order));
    }

    @Operation(summary = "更新工单")
    @PutMapping("/{id}")
    public Result<BizWorkOrder> update(@PathVariable Long id, @RequestBody BizWorkOrder order) {
        return Result.success(workOrderService.update(id, order));
    }

    @Operation(summary = "指派工单")
    @PutMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @RequestParam Long assigneeId) {
        workOrderService.assign(id, assigneeId);
        return Result.success();
    }

    @Operation(summary = "变更工单状态")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        workOrderService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "工单评论列表")
    @GetMapping("/{orderId}/comments")
    public Result<List<BizWorkOrderComment>> listComments(@PathVariable Long orderId) {
        return Result.success(workOrderService.listComments(orderId));
    }

    @Operation(summary = "新增评论")
    @PostMapping("/{orderId}/comments")
    public Result<BizWorkOrderComment> addComment(
            @PathVariable Long orderId, @RequestBody BizWorkOrderComment comment) {
        return Result.success(workOrderService.addComment(orderId, comment));
    }
}
