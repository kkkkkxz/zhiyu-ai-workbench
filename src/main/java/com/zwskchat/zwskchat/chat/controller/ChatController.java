package com.zwskchat.zwskchat.chat.controller;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.chat.dto.CompletionRequest;
import com.zwskchat.zwskchat.chat.dto.CreateSessionRequest;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.service.ChatService;
import com.zwskchat.zwskchat.chat.service.SessionService;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Tag(name = "AI 对话")
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
// 全部端点要求登录；service 层 SessionService.getOwn 再做 user 维度的归属校验，
// 防御 IDOR（同租户其他用户的 sessionId 被遍历）。
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final SessionService sessionService;
    private final ChatService chatService;

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    @AuditLog(operationType = "CHAT_SESSION_CREATE", targetType = "session",
              targetIdExpr = "#ret.data?.id")
    public Result<AiSession> createSession(@RequestBody @Valid CreateSessionRequest req) {
        return Result.success(sessionService.create(req));
    }

    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    public Result<List<AiSession>> mySessions() {
        return Result.success(sessionService.listMine());
    }

    @Operation(summary = "会话详情")
    @GetMapping("/sessions/{id}")
    public Result<AiSession> sessionDetail(@PathVariable Long id) {
        return Result.success(sessionService.getOwn(id));
    }

    @Operation(summary = "会话消息列表")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<AiMessage>> messages(@PathVariable Long id) {
        return Result.success(sessionService.listMessages(id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    @AuditLog(operationType = "CHAT_SESSION_DELETE", targetType = "session", targetIdExpr = "#id")
    public Result<Void> deleteSession(@PathVariable Long id) {
        sessionService.delete(id);
        return Result.success();
    }

    @Operation(summary = "RAG 流式问答（SSE）",
               description = "事件类型：references / delta / done / error")
    @PostMapping(value = "/sessions/{id}/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "chat:stream", limit = 30, window = 60, dimension = RateLimit.Dimension.USER)
    @AuditLog(operationType = "CHAT_COMPLETIONS_STREAM", targetType = "session", targetIdExpr = "#id")
    public SseEmitter stream(@PathVariable Long id, @RequestBody @Valid CompletionRequest req) {
        return chatService.completionsStream(id, req);
    }
}
