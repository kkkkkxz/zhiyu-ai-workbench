package com.zwskchat.zwskchat.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_work_order")
public class BizWorkOrder {
    private Long id;
    private Long tenantId;
    private String orderNo;
    private String title;
    private String type;
    private String priority;
    private String status;
    private Long customerId;
    private Long reporterId;
    private Long assigneeId;
    private Long deptId;
    private String description;
    private String aiCategory;
    private String aiSuggestion;
    private String aiAssignedReason;
    private String aiSimilarIds;
    private String aiResolutionHint;
    private LocalDateTime slaDeadline;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
