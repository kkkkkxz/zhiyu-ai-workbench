package com.zwskchat.zwskchat.voice.keyword.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class VoiceKeywordBatchUpsertRequest {
    @Size(max = 20, message = "单次最多保存 20 个关键词")
    private List<@Size(max = 64, message = "关键词不能超过 64 个字符") String> keywords;

    @Size(max = 1000, message = "来源文本不能超过 1000 个字符")
    private String sourceText;

    private Long sessionId;

    @Size(max = 128, message = "turnId 不能超过 128 个字符")
    private String turnId;
}
