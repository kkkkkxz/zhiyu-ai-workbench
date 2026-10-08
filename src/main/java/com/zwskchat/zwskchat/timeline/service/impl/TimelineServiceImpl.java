package com.zwskchat.zwskchat.timeline.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.timeline.entity.BizTimelineEvent;
import com.zwskchat.zwskchat.timeline.mapper.BizTimelineEventMapper;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final BizTimelineEventMapper eventMapper;

    @Async
    @Override
    public void record(String bizType, Long bizId, String eventType, String title, String content) {
        try {
            BizTimelineEvent event = buildEvent(bizType, bizId, eventType, title, "user");
            event.setContent(content);
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[Timeline] record failed biz={}/{} err={}", bizType, bizId, e.getMessage());
        }
    }

    @Async
    @Override
    public void recordFieldChange(String bizType, Long bizId, String fieldName, String oldValue, String newValue) {
        try {
            BizTimelineEvent event = buildEvent(bizType, bizId, "updated",
                    fieldName + " 变更: " + oldValue + " → " + newValue, "user");
            event.setFieldName(fieldName);
            event.setOldValue(oldValue);
            event.setNewValue(newValue);
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[Timeline] recordFieldChange failed biz={}/{} err={}", bizType, bizId, e.getMessage());
        }
    }

    @Async
    @Override
    public void recordAiAction(String bizType, Long bizId, String title, String content) {
        try {
            BizTimelineEvent event = buildEvent(bizType, bizId, "ai_action", title, "ai");
            event.setContent(content);
            eventMapper.insert(event);
        } catch (Exception e) {
            log.warn("[Timeline] recordAiAction failed biz={}/{} err={}", bizType, bizId, e.getMessage());
        }
    }

    @Override
    public List<BizTimelineEvent> listByBiz(String bizType, Long bizId) {
        return eventMapper.selectList(Wrappers.<BizTimelineEvent>lambdaQuery()
                .eq(BizTimelineEvent::getBizType, bizType)
                .eq(BizTimelineEvent::getBizId, bizId)
                .orderByDesc(BizTimelineEvent::getCreateTime)
                .last("LIMIT 200"));
    }

    private BizTimelineEvent buildEvent(String bizType, Long bizId, String eventType, String title, String source) {
        LoginUser user = SecurityUtils.currentUserOrNull();
        BizTimelineEvent event = new BizTimelineEvent();
        if (user != null) {
            event.setTenantId(user.getTenantId());
            event.setOperatorId(user.getUserId());
            event.setOperatorName(user.getUsername());
        } else {
            event.setTenantId(0L);
            event.setOperatorId(0L);
            event.setOperatorName("system");
        }
        event.setBizType(bizType);
        event.setBizId(bizId);
        event.setEventType(eventType);
        event.setTitle(title);
        event.setSource(source);
        return event;
    }
}
