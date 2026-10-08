package com.zwskchat.zwskchat.datascope.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_data_scope")
public class SysDataScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    private String bizType;

    private String scopeType;

    private String customDeptIds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
