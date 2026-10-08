package com.zwskchat.zwskchat.chat.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.chat.dto.CreateSessionRequest;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.chat.service.SessionService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;

    @Override
    public AiSession create(CreateSessionRequest req) {
        AiSession s = new AiSession();
        s.setTenantId(SecurityUtils.currentUser().getTenantId());
        s.setUserId(SecurityUtils.currentUserId());
        s.setTitle(req.getTitle() == null || req.getTitle().isBlank() ? "新会话" : req.getTitle());
        s.setSessionType(req.getSessionType() == null ? "TEXT" : req.getSessionType());
        s.setStatus(1);
        if (req.getKbIds() != null && !req.getKbIds().isEmpty()) {
            s.setKbIds(req.getKbIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(s);
        return s;
    }

    @Override
    public AiSession getOwn(Long sessionId) {
        AiSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        if (!s.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        return s;
    }

    @Override
    public List<AiSession> listMine() {
        return sessionMapper.selectList(Wrappers.<AiSession>lambdaQuery()
                .eq(AiSession::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(AiSession::getUpdateTime));
    }

    @Override
    public List<AiMessage> listMessages(Long sessionId) {
        getOwn(sessionId);
        return messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime));
    }

    @Override
    public void delete(Long sessionId) {
        getOwn(sessionId);
        sessionMapper.deleteById(sessionId);
    }
}
