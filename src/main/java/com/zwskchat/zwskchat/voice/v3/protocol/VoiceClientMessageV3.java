package com.zwskchat.zwskchat.voice.v3.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceClientMessageV3 {
    /** HELLO / START_TURN / AUDIO_END / INTERRUPT / RESET / PING */
    private String type;
    /** PTT / HANDS_FREE / VISUAL */
    private String mode;
    private Long sessionId;
    private List<Long> kbIds;
    /** 当前回答使用的人设 ID，例如 general / receptionist / tech_support */
    private String personaId;
    private Boolean globalKb;
    private Integer sampleRate;
    /** 客户端上传音频格式，当前固定 pcm-16khz-16bit-mono */
    private String inputAudioFormat;
    private Map<String, Object> client;
}
