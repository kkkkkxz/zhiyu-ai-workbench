package com.zwskchat.zwskchat.tenant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tenant_feature_config")
public class TenantFeatureConfig {

    private Long id;
    private Long tenantId;
    private String featureCode;
    private String featureName;
    private String category;
    private String description;
    private String sourceTemplateCode;
    private Integer enabled;
    private String configJson;
    private Integer sort;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
