package com.zwskchat.zwskchat.crm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_follow_up")
public class BizFollowUp {
    private Long id;
    private Long tenantId;
    private Long customerId;
    private Long userId;
    private String type;
    private String content;
    private String aiSummary;
    private String nextPlan;
    private LocalDateTime nextTime;
    private LocalDateTime createTime;
}
