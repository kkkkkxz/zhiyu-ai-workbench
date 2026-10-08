package com.zwskchat.zwskchat.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessage {
    private Long id;
    private Long tenantId;
    private Long sessionId;
    private Long userId;
    /** user / assistant / system */
    private String role;
    private String content;
    private String audioUrl;
    private Integer latencyMs;
    private Integer tokenIn;
    private Integer tokenOut;
    private Integer feedback;
    /** 生成该回答的模型标识（如 qwen-plus / gpt-4o-mini），用于回溯。 */
    private String modelId;
    /** system+context 提示词的 SHA-256，便于回归测试与问题回溯。 */
    private String promptHash;
    /** 生成时生效的治理/安全策略版本。 */
    private String policyVersion;
    /** 组合可信度评分 [0,1]。 */
    private BigDecimal confidenceScore;
    /** 可信度三档决策 ANSWER / CAUTIOUS / REFUSE。 */
    private String confidenceDecision;
    private LocalDateTime createTime;
}
