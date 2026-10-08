package com.zwskchat.zwskchat.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_dispatch_assignee")
public class BizDispatchAssignee {
    private Long id;
    private Long dispatchId;
    private Long userId;
    private String status;
    private Integer progress;
    private String note;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
}
