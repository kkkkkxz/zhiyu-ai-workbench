package com.zwskchat.zwskchat.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notification")
public class SysNotification {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Integer isRead;
    private String channel;
    private String sendStatus;
    private LocalDateTime createTime;
}
