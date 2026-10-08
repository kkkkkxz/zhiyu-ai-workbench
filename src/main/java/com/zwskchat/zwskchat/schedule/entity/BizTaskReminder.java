package com.zwskchat.zwskchat.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_task_reminder")
public class BizTaskReminder {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    private String content;
    private LocalDateTime remindTime;
    private String remindType;
    private String channel;
    private String bizType;
    private Long bizId;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime createTime;
}
