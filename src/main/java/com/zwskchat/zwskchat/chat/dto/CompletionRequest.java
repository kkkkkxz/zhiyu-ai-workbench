package com.zwskchat.zwskchat.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CompletionRequest {
    @NotBlank
    @Size(max = 4000, message = "单次问题最多 4000 字符")
    private String question;

    /** 可覆盖会话上配置的 kbIds */
    private List<Long> kbIds;

    /** 当前回答使用的人设 ID，例如 general / receptionist / tech_support */
    @Size(max = 64)
    private String personaId;
}
