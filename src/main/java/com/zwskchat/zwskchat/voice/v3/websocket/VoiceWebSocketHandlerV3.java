package com.zwskchat.zwskchat.voice.v3.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.voice.v3.core.VoiceSessionV3;
import com.zwskchat.zwskchat.voice.v3.orchestrator.VoiceOrchestratorV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceClientMessageV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceServerMessageV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandlerV3 extends AbstractWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final VoiceSessionRegistryV3 registry;
    private final VoiceOrchestratorV3 orchestrator;

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        Long userId = (Long) ws.getAttributes().get("userId");
        Long tenantId = (Long) ws.getAttributes().get("tenantId");
        String username = (String) ws.getAttributes().get("username");
        boolean publicJarvis = Boolean.TRUE.equals(ws.getAttributes().get("publicJarvis"));
        // 匿名 Jarvis 单连接消息预算从 HandshakeInterceptor 透传过来；非 public 路径默认 0=不限
        Object budgetAttr = ws.getAttributes().get("publicJarvisMaxMessages");
        int messageBudget = budgetAttr instanceof Number n ? n.intValue() : 0;
        if (userId == null || tenantId == null) {
            ws.close(CloseStatus.POLICY_VIOLATION.withReason("auth missing"));
            return;
        }
        VoiceSessionV3 session = new VoiceSessionV3(ws, userId, tenantId, username,
                publicJarvis, messageBudget);
        registry.put(session);
        orchestrator.onConnected(session);
        log.info("[Voice-V3] connected conn={}, userId={}, tenantId={}, public={}, budget={}",
                ws.getId(), userId, tenantId, publicJarvis, messageBudget);
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        VoiceSessionV3 session = registry.get(ws.getId());
        if (session == null) return;
        try {
            VoiceClientMessageV3 msg = objectMapper.readValue(message.getPayload(), VoiceClientMessageV3.class);
            orchestrator.onClientMessage(session, msg);
        } catch (Exception e) {
            log.warn("[Voice-V3] text message failed conn={}: {}", ws.getId(), e.getMessage(), e);
            orchestrator.send(session, VoiceServerMessageV3.error(1001, "请求格式不合法", true));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession ws, BinaryMessage message) {
        VoiceSessionV3 session = registry.get(ws.getId());
        if (session == null) return;
        byte[] audio = new byte[message.getPayload().remaining()];
        message.getPayload().get(audio);
        orchestrator.onAudio(session, audio);
    }

    @Override
    public void handleTransportError(WebSocketSession ws, Throwable exception) {
        log.warn("[Voice-V3] transport error conn={}, err={}", ws.getId(), exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        VoiceSessionV3 session = registry.remove(ws.getId());
        if (session != null) orchestrator.close(session);
        log.info("[Voice-V3] closed conn={}, status={}", ws.getId(), status);
    }
}
