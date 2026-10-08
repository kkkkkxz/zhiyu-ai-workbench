package com.zwskchat.zwskchat.timeline.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_timeline_event")
public class BizTimelineEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String bizType;

    private Long bizId;

    private String eventType;

    private String title;

    private String content;

    private String oldValue;

    private String newValue;

    private String fieldName;

    private Long operatorId;

    private String operatorName;

    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
