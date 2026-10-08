package com.zwskchat.zwskchat.voice.v3.websocket;

import com.zwskchat.zwskchat.voice.v3.core.VoiceSessionV3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class VoiceSessionRegistryV3 {
    private final Map<String, VoiceSessionV3> sessions = new ConcurrentHashMap<>();

    public void put(VoiceSessionV3 session) {
        sessions.put(session.getConnectionId(), session);
        log.info("[Voice-V3] registry put conn={}, total={}", session.getConnectionId(), sessions.size());
    }

    public VoiceSessionV3 get(String connectionId) {
        return sessions.get(connectionId);
    }

    public VoiceSessionV3 remove(String connectionId) {
        VoiceSessionV3 removed = sessions.remove(connectionId);
        if (removed != null) {
            log.info("[Voice-V3] registry remove conn={}, total={}", connectionId, sessions.size());
        }
        return removed;
    }

    public int size() {
        return sessions.size();
    }
}
