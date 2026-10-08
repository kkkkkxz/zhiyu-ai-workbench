package com.zwskchat.zwskchat.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_schedule")
public class BizSchedule {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    private String description;
    private String type;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer allDay;
    private String location;
    private String bizType;
    private Long bizId;
    private String recurrence;
    private String color;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
