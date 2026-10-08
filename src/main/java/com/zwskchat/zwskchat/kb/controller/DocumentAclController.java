package com.zwskchat.zwskchat.kb.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.kb.dto.DocumentAclItem;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentAclRequest;
import com.zwskchat.zwskchat.kb.service.DocumentAclService;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "文档查看权限")
@RestController
@RequestMapping("/kb/{kbId}/docs/{docId}/acl")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocumentAclController {

    private final DocumentAclService documentAclService;
    private final KnowledgeBaseService kbService;

    @Operation(summary = "查看文档 ACL 列表")
    @GetMapping
    public Result<List<DocumentAclItem>> list(@PathVariable Long kbId, @PathVariable Long docId) {
        kbService.assertWritable(kbId);
        return Result.success(documentAclService.listByDoc(docId));
    }

    @Operation(summary = "更新文档 ACL")
    @PutMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','TENANT_ADMIN','KB_ADMIN')")
    public Result<Void> update(@PathVariable Long kbId, @PathVariable Long docId,
                               @RequestBody @Valid UpdateDocumentAclRequest request) {
        kbService.assertWritable(kbId);
        documentAclService.updateAcl(docId, request);
        return Result.success();
    }
}
