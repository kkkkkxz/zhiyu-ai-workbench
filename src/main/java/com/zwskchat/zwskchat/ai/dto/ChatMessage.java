package com.zwskchat.zwskchat.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    /** system / user / assistant */
    private String role;
    private String content;

    public static ChatMessage system(String c) { return new ChatMessage("system", c); }
    public static ChatMessage user(String c) { return new ChatMessage("user", c); }
    public static ChatMessage assistant(String c) { return new ChatMessage("assistant", c); }
}
