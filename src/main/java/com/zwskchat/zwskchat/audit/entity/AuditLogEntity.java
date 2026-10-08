package com.zwskchat.zwskchat.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLogEntity {
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String operatorName;
    private String operationType;
    private String targetType;
    private String targetId;
    /** SUCCESS / FAILED */
    private String result;
    private String ip;
    private String userAgent;
    private String requestUri;
    private String requestMethod;
    private Integer costMs;
    private String detail;
    private LocalDateTime createTime;
}
