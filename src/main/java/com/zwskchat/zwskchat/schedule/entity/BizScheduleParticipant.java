package com.zwskchat.zwskchat.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_schedule_participant")
public class BizScheduleParticipant {
    private Long id;
    private Long scheduleId;
    private Long userId;
    private String response;
    private LocalDateTime createTime;
}
