package com.zwskchat.zwskchat.voice.v3.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceServerMessageV3 {
    /** READY / SESSION / STATE / ASR_PARTIAL / ASR_FINAL / REFERENCES / LLM_DELTA / LLM_DONE / TTS_BEGIN / TTS_END / TURN_END / ERROR / PONG */
    private String type;
    private String state;
    private String text;
    private String turnId;
    private String sessionId;
    private String messageId;
    private String audioFormat;
    private Integer code;
    private Boolean recoverable;
    private List<Map<String, Object>> references;
    private Map<String, Object> meta;

    public static VoiceServerMessageV3 ready(String connectionId, String inputAudioFormat) {
        return builder()
                .type("READY")
                .meta(Map.of("connectionId", connectionId, "inputAudioFormat", inputAudioFormat))
                .build();
    }

    public static VoiceServerMessageV3 state(VoiceStateV3 state) {
        return builder().type("STATE").state(state.name()).build();
    }

    public static VoiceServerMessageV3 session(Long sessionId) {
        return builder().type("SESSION").sessionId(sessionId == null ? null : String.valueOf(sessionId)).build();
    }

    public static VoiceServerMessageV3 asrPartial(String turnId, String text) {
        return builder().type("ASR_PARTIAL").turnId(turnId).text(text).build();
    }

    public static VoiceServerMessageV3 asrFinal(String turnId, String text) {
        return builder().type("ASR_FINAL").turnId(turnId).text(text).build();
    }

    public static VoiceServerMessageV3 references(String turnId, List<Map<String, Object>> refs) {
        return builder().type("REFERENCES").turnId(turnId).references(refs).build();
    }

    public static VoiceServerMessageV3 llmDelta(String turnId, String text) {
        return builder().type("LLM_DELTA").turnId(turnId).text(text).build();
    }

    public static VoiceServerMessageV3 llmDone(String turnId, Long messageId) {
        return builder().type("LLM_DONE").turnId(turnId).messageId(messageId == null ? null : String.valueOf(messageId)).build();
    }

    public static VoiceServerMessageV3 ttsBegin(String turnId, String text, String audioFormat) {
        return builder().type("TTS_BEGIN").turnId(turnId).text(text).audioFormat(audioFormat).build();
    }

    public static VoiceServerMessageV3 ttsEnd(String turnId) {
        return builder().type("TTS_END").turnId(turnId).build();
    }

    public static VoiceServerMessageV3 turnEnd(String turnId, String state) {
        return builder().type("TURN_END").turnId(turnId).state(state).build();
    }

    public static VoiceServerMessageV3 error(int code, String text, boolean recoverable) {
        return builder().type("ERROR").code(code).text(text).recoverable(recoverable).build();
    }

    public static VoiceServerMessageV3 pong() {
        return builder().type("PONG").build();
    }
}
