package com.zwskchat.zwskchat.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateSessionRequest {
    @Size(max = 255)
    private String title;
    /** 关联的知识库 ID 列表 */
    private List<Long> kbIds;
    private String sessionType = "TEXT";
}
