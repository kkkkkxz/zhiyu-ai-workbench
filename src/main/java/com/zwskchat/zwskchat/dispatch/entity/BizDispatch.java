package com.zwskchat.zwskchat.dispatch.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_dispatch")
public class BizDispatch {
    private Long id;
    private Long tenantId;
    private String title;
    private String description;
    private String type;
    private String priority;
    private Long dispatcherId;
    private Long deptId;
    private LocalDateTime dueDate;
    private String status;
    private String completionNote;
    private LocalDateTime completedAt;
    private String bizType;
    private Long bizId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
