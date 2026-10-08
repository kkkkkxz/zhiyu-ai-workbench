package com.zwskchat.zwskchat.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_message_reference")
public class AiMessageReference {
    private Long id;
    private Long tenantId;
    private Long messageId;
    private Long documentId;
    private Long chunkId;
    private BigDecimal score;
    private String contentSnapshot;
    private LocalDateTime createTime;
}
