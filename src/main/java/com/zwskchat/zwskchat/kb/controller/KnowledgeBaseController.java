package com.zwskchat.zwskchat.kb.controller;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.kb.dto.CreateKbRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeBase;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "知识库")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
// 类级声明：本 controller 所有端点都必须登录。Spring Security 的 anyRequest().authenticated()
// 已经在 SecurityConfig 全局兜底；这里再次显式声明，让"必须登录"成为代码可读的契约——
// 方法级 @PreAuthorize 会覆盖类级，未来要给单个 endpoint 收紧（如 hasAuthority('kb:create'）），
// 直接在方法上写即可。
@PreAuthorize("isAuthenticated()")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @Operation(summary = "创建知识库")
    @PostMapping
    @AuditLog(operationType = "KB_CREATE", targetType = "kb", targetIdExpr = "#ret.data?.id")
    public Result<KnowledgeBase> create(@RequestBody @Valid CreateKbRequest req) {
        return Result.success(kbService.create(req));
    }

    @Operation(summary = "知识库列表（当前租户）")
    @GetMapping
    public Result<List<KnowledgeBase>> list() {
        return Result.success(kbService.listMine());
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/{id}")
    public Result<KnowledgeBase> get(@PathVariable Long id) {
        // 安全要点：必须走 getReadable —— 它会校验 visibility / owner / admin。
        // 历史 bug：曾用 getById 仅靠租户拦截器做兜底，导致同租户任何登录用户能枚举
        // PRIVATE 知识库的元数据（IDOR）。详情参考 H1 安全审计修复。
        return Result.success(kbService.getReadable(id));
    }

    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    @AuditLog(operationType = "KB_DELETE", targetType = "kb", targetIdExpr = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.success();
    }
}
