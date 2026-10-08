package com.zwskchat.zwskchat.chat.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_session")
public class AiSession {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    /** TEXT / VOICE */
    private String sessionType;
    /** 关联的 kb_id 列表，逗号分隔 */
    private String kbIds;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
