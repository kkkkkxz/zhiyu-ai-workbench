package com.zwskchat.zwskchat.chat.service;

import com.zwskchat.zwskchat.chat.dto.CompletionRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    /**
     * 发起一次基于 RAG 的流式问答。
     * - 立刻返回一个 SseEmitter，控制器写到响应；
     * - 后台线程做检索 + LLM 流式调用，token 通过 SseEmitter 推送；
     * - 完成后落库 user/assistant 两条消息 + 引用记录。
     */
    SseEmitter completionsStream(Long sessionId, CompletionRequest req);
}
