package com.zwskchat.zwskchat.attachment.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_attachment_analysis")
public class BizAttachmentAnalysis {
    private Long id;
    private Long tenantId;
    private Long attachmentId;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String fileHash;
    private String status;
    private String riskLevel;
    private String documentType;
    private String summary;
    private String keyFieldsJson;
    private String missingFieldsJson;
    private String risksJson;
    private String suggestionsJson;
    private String nextActionsJson;
    private String rawAiResponse;
    private String parseError;
    private Integer parsedTextChars;
    private Long analyzedBy;
    private LocalDateTime analyzedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
