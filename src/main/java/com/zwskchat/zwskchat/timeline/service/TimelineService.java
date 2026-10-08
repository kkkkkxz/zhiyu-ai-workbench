package com.zwskchat.zwskchat.timeline.service;

import com.zwskchat.zwskchat.timeline.entity.BizTimelineEvent;

import java.util.List;

public interface TimelineService {

    void record(String bizType, Long bizId, String eventType, String title, String content);

    void recordFieldChange(String bizType, Long bizId, String fieldName, String oldValue, String newValue);

    void recordAiAction(String bizType, Long bizId, String title, String content);

    List<BizTimelineEvent> listByBiz(String bizType, Long bizId);
}
