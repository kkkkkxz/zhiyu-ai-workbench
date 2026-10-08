package com.zwskchat.zwskchat.ai.hub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_action_definition")
public class AiActionDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String actionCode;

    private String name;

    private String description;

    private String bizType;

    private String riskLevel;

    private String permissionCode;

    private String paramSchema;

    private String previewTemplate;

    private String executorBean;

    private Integer confirmRequired;

    private Integer enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
