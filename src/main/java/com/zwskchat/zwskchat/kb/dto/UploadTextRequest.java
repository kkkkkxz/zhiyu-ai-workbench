package com.zwskchat.zwskchat.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UploadTextRequest {
    @NotBlank
    @Size(max = 255)
    private String fileName;

    /** 文本正文，支持 .txt / .md。最大 1 MB（约 1M 字符） */
    @NotBlank
    @Size(max = 1_000_000, message = "单个文档正文最多 100 万字符")
    private String content;
}
