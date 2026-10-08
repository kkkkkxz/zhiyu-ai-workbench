package com.zwskchat.zwskchat.ai.hub.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_action_execution_log")
public class AiActionExecutionLog {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String actionCode;
    private String actionName;
    private String bizType;
    private Long bizId;
    private String phase;
    private String riskLevel;
    private String userInput;
    private String previewJson;
    private String resultJson;
    private String status;
    private String errorMessage;
    private String traceId;
    private LocalDateTime createTime;
}
