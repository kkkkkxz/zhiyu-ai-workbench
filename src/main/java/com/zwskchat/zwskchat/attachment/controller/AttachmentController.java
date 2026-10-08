package com.zwskchat.zwskchat.attachment.controller;

import com.zwskchat.zwskchat.attachment.dto.AttachmentDownloadResponse;
import com.zwskchat.zwskchat.attachment.dto.AttachmentAnalysisResponse;
import com.zwskchat.zwskchat.attachment.entity.BizAttachment;
import com.zwskchat.zwskchat.attachment.service.AttachmentAnalysisService;
import com.zwskchat.zwskchat.attachment.service.AttachmentService;
import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "通用附件中心")
@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentAnalysisService analysisService;

    @Operation(summary = "业务附件列表")
    @GetMapping("/{bizType}/{bizId}")
    public Result<List<BizAttachment>> listByBiz(@PathVariable String bizType, @PathVariable Long bizId) {
        return Result.success(attachmentService.listByBiz(bizType, bizId));
    }

    @Operation(summary = "业务附件 AI 解析结果列表")
    @GetMapping("/{bizType}/{bizId}/analysis")
    public Result<List<AttachmentAnalysisResponse>> listAnalysis(@PathVariable String bizType, @PathVariable Long bizId) {
        return Result.success(analysisService.listByBiz(bizType, bizId));
    }

    @Operation(summary = "上传业务附件")
    @PostMapping(value = "/{bizType}/{bizId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AuditLog(operationType = "BIZ_ATTACHMENT_UPLOAD", targetType = "attachment", recordArgs = false)
    public Result<BizAttachment> upload(@PathVariable String bizType,
                                        @PathVariable Long bizId,
                                        @RequestParam(defaultValue = "general") String category,
                                        @RequestPart("file") MultipartFile file) {
        return Result.success(attachmentService.upload(bizType, bizId, category, file));
    }

    @Operation(summary = "生成附件下载地址")
    @GetMapping("/{attachmentId}/download-url")
    public Result<AttachmentDownloadResponse> createDownloadUrl(@PathVariable Long attachmentId) {
        return Result.success(attachmentService.createDownloadUrl(attachmentId));
    }

    @Operation(summary = "单个附件 AI 解析结果")
    @GetMapping("/{attachmentId}/analysis")
    public Result<AttachmentAnalysisResponse> getAnalysis(@PathVariable Long attachmentId) {
        return Result.success(analysisService.getByAttachment(attachmentId));
    }

    @Operation(summary = "重跑附件 AI 解析")
    @PostMapping("/{attachmentId}/analysis/retry")
    @AuditLog(operationType = "BIZ_ATTACHMENT_ANALYSIS_RETRY", targetType = "attachment", targetIdExpr = "#attachmentId")
    public Result<AttachmentAnalysisResponse> retryAnalysis(@PathVariable Long attachmentId) {
        return Result.success(analysisService.retry(attachmentId));
    }

    @Operation(summary = "删除业务附件")
    @DeleteMapping("/{attachmentId}")
    @AuditLog(operationType = "BIZ_ATTACHMENT_DELETE", targetType = "attachment", targetIdExpr = "#attachmentId")
    public Result<Void> delete(@PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
        return Result.success();
    }
}
