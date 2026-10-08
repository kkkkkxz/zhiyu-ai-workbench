package com.zwskchat.zwskchat.tenant.controller;

import com.zwskchat.zwskchat.common.api.PageResult;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyRequest;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyResponse;
import com.zwskchat.zwskchat.tenant.dto.AdminApplyReviewRequest;
import com.zwskchat.zwskchat.tenant.service.AdminApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员审核")
@RestController
@RequestMapping("/admin-applies")
@RequiredArgsConstructor
@Validated
public class AdminApplyController {

    private final AdminApplyService adminApplyService;

    @Operation(summary = "提交管理员申请")
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN')")
    public Result<AdminApplyResponse> submit(@RequestBody @Valid AdminApplyRequest request) {
        return Result.success(adminApplyService.submit(request));
    }

    @Operation(summary = "待审核列表")
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<PageResult<AdminApplyResponse>> listPending(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) long size) {
        return Result.success(adminApplyService.listPending(page, size));
    }

    @Operation(summary = "通过申请")
    @PostMapping("/{applyId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<AdminApplyResponse> approve(@PathVariable Long applyId) {
        return Result.success(adminApplyService.approve(applyId));
    }

    @Operation(summary = "驳回申请")
    @PostMapping("/{applyId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<AdminApplyResponse> reject(@PathVariable Long applyId,
                                              @RequestBody(required = false) AdminApplyReviewRequest request) {
        String reason = request == null ? null : request.getRejectReason();
        return Result.success(adminApplyService.reject(applyId, reason));
    }
}
