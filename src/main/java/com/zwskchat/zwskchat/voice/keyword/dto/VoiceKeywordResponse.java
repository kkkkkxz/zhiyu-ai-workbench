package com.zwskchat.zwskchat.voice.keyword.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VoiceKeywordResponse {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long sessionId;
    private String turnId;
    private String keyword;
    private String sourceType;
    private String sourceText;
    private Integer hitCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
