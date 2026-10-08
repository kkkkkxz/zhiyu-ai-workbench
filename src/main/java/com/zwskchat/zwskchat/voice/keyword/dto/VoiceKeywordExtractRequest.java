package com.zwskchat.zwskchat.voice.keyword.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VoiceKeywordExtractRequest {
    @NotBlank(message = "文本不能为空")
    @Size(max = 1000, message = "文本不能超过 1000 个字符")
    private String text;

    /** 最多返回多少个候选关键词，默认 8，上限 20 */
    private Integer limit;
}
