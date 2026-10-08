package com.zwskchat.zwskchat.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wf_definition")
public class WfDefinition {
    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    private String nodesJson;
    private Integer version;
    private Integer status;
    private LocalDateTime createTime;
}
