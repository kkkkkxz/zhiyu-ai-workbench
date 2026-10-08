package com.zwskchat.zwskchat.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_task")
public class WfTask {
    private Long id;
    private Long tenantId;
    private Long instanceId;
    private String nodeCode;
    private String nodeName;
    private Long assigneeId;
    private String action;
    private String comment;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
