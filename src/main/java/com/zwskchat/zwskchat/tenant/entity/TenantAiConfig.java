package com.zwskchat.zwskchat.tenant.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tenant_ai_config")
public class TenantAiConfig {

    private Long id;
    private Long tenantId;
    private String provider;
    private String baseUrl;
    private String apiKeyEnc;
    private String apiKeyMask;
    private String chatModel;
    private String embeddingModel;
    private String visionModel;
    private Double temperature;
    private Integer enabled;
    private String remark;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
