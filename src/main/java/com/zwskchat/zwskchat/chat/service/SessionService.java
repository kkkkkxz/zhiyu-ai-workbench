package com.zwskchat.zwskchat.chat.service;

import com.zwskchat.zwskchat.chat.dto.CreateSessionRequest;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiSession;

import java.util.List;

public interface SessionService {
    AiSession create(CreateSessionRequest req);
    AiSession getOwn(Long sessionId);
    List<AiSession> listMine();
    List<AiMessage> listMessages(Long sessionId);
    void delete(Long sessionId);
}
