package com.zwskchat.zwskchat.tenant.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.tenant.dto.*;
import com.zwskchat.zwskchat.tenant.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/tenants/{tenantId}/departments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "部门树")
    @GetMapping
    public Result<List<DepartmentResponse>> tree(@PathVariable Long tenantId) {
        return Result.success(departmentService.tree(tenantId));
    }

    @Operation(summary = "创建部门")
    @PostMapping
    public Result<DepartmentResponse> create(@PathVariable Long tenantId,
                                              @RequestBody @Valid CreateDepartmentRequest request) {
        return Result.success(departmentService.create(tenantId, request));
    }

    @Operation(summary = "更新部门")
    @PutMapping("/{deptId}")
    public Result<DepartmentResponse> update(@PathVariable Long tenantId,
                                              @PathVariable Long deptId,
                                              @RequestBody @Valid UpdateDepartmentRequest request) {
        return Result.success(departmentService.update(tenantId, deptId, request));
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{deptId}")
    public Result<Void> delete(@PathVariable Long tenantId, @PathVariable Long deptId) {
        departmentService.delete(tenantId, deptId);
        return Result.success();
    }

    @Operation(summary = "设置用户所属部门")
    @PutMapping("/users/{userId}")
    public Result<Void> assignUser(@PathVariable Long tenantId,
                                   @PathVariable Long userId,
                                   @RequestBody @Valid AssignUserDepartmentRequest request) {
        departmentService.assignUser(tenantId, userId, request.getDeptId());
        return Result.success();
    }

    @Operation(summary = "查看用户上下级关系")
    @GetMapping("/reporting/{userId}")
    public Result<List<ReportingRelationResponse>> listReporting(@PathVariable Long tenantId,
                                                                  @PathVariable Long userId) {
        return Result.success(departmentService.listReporting(tenantId, userId));
    }

    @Operation(summary = "添加上下级关系")
    @PostMapping("/reporting")
    public Result<ReportingRelationResponse> addReporting(@PathVariable Long tenantId,
                                                          @RequestBody @Valid ReportingRelationRequest request) {
        return Result.success(departmentService.addReporting(tenantId, request));
    }

    @Operation(summary = "删除上下级关系")
    @DeleteMapping("/reporting/{relationId}")
    public Result<Void> removeReporting(@PathVariable Long tenantId, @PathVariable Long relationId) {
        departmentService.removeReporting(tenantId, relationId);
        return Result.success();
    }
}
