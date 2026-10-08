package com.zwskchat.zwskchat.kb.controller;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.kb.dto.UploadTextRequest;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentGovernanceRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import com.zwskchat.zwskchat.kb.service.DocumentService;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "知识文档")
@RestController
@RequestMapping("/kb/{kbId}/docs")
@RequiredArgsConstructor
// 类级 @PreAuthorize：本 controller 全部 endpoint 必须登录；service 层再做 KB 维度的
// owner / admin 校验。两层防御让"误删 controller 的 service 校验"也不至于完全失守。
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    private final DocumentService docService;

    @Operation(summary = "上传纯文本/Markdown 并同步索引（M1）")
    @PostMapping("/upload-text")
    @RateLimit(key = "kb:upload", limit = 10, window = 60)
    @AuditLog(operationType = "KB_DOC_UPLOAD_TEXT", targetType = "kb",
              targetIdExpr = "#kbId", recordArgs = false)
    public Result<KnowledgeDocument> uploadText(@PathVariable Long kbId,
                                                @RequestBody @Valid UploadTextRequest req) {
        return Result.success(docService.uploadText(kbId, req));
    }

    @Operation(summary = "上传二进制文档（PDF / DOCX / PPTX / XLSX / HTML / RTF / TXT / MD ...）",
               description = "存入 MinIO + 异步 Tika 解析 + 索引；前端轮询 parseStatus。")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(key = "kb:upload-file", limit = 10, window = 60)
    @AuditLog(operationType = "KB_DOC_UPLOAD_FILE", targetType = "kb",
              targetIdExpr = "#kbId", recordArgs = false)
    public Result<KnowledgeDocument> uploadFile(@PathVariable Long kbId,
                                                @RequestPart("file") MultipartFile file) {
        return Result.success(docService.uploadFile(kbId, file));
    }

    @Operation(summary = "知识库下文档列表")
    @GetMapping
    public Result<List<KnowledgeDocument>> list(@PathVariable Long kbId) {
        return Result.success(docService.listByKb(kbId));
    }

    @Operation(summary = "录入/更新文档知识治理元数据（资产类型/权威等级/时效/密级/可见范围）")
    @PatchMapping("/{docId}/governance")
    @AuditLog(operationType = "KB_DOC_GOVERNANCE_UPDATE", targetType = "document", targetIdExpr = "#docId")
    public Result<KnowledgeDocument> updateGovernance(@PathVariable Long kbId,
                                                      @PathVariable Long docId,
                                                      @RequestBody @Valid UpdateDocumentGovernanceRequest req) {
        return Result.success(docService.updateGovernance(docId, req));
    }

    @Operation(summary = "删除文档（含其切片）")
    @DeleteMapping("/{docId}")
    @AuditLog(operationType = "KB_DOC_DELETE", targetType = "document", targetIdExpr = "#docId")
    public Result<Void> delete(@PathVariable Long kbId, @PathVariable Long docId) {
        docService.delete(docId);
        return Result.success();
    }
}
