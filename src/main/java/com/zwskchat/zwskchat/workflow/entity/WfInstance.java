package com.zwskchat.zwskchat.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_instance")
public class WfInstance {
    private Long id;
    private Long tenantId;
    private Long definitionId;
    private String bizType;
    private Long bizId;
    private String title;
    private Long initiatorId;
    private String currentNode;
    private String status;
    private String formData;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
