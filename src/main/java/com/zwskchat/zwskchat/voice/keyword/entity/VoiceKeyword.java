package com.zwskchat.zwskchat.voice.keyword.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("voice_keyword")
public class VoiceKeyword {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long sessionId;
    private String turnId;
    private String keyword;
    /** AUTO / MANUAL */
    private String sourceType;
    /** 触发该关键词的人类原始发言快照 */
    private String sourceText;
    private Integer hitCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
