package com.zwskchat.zwskchat.voice.keyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VoiceKeywordUpsertRequest {
    @NotBlank(message = "关键词不能为空")
    @Size(max = 64, message = "关键词不能超过 64 个字符")
    private String keyword;

    @Size(max = 1000, message = "来源文本不能超过 1000 个字符")
    private String sourceText;

    private Long sessionId;

    @Size(max = 128, message = "turnId 不能超过 128 个字符")
    private String turnId;
}
