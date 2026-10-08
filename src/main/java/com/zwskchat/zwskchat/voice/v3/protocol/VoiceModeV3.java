package com.zwskchat.zwskchat.voice.v3.protocol;

public enum VoiceModeV3 {
    /** 按住/点击开始，客户端显式结束 */
    PTT,
    /** 免手动连续对话：客户端用 AudioWorklet 电平自动结束一轮 */
    HANDS_FREE,
    /** 只做实时字幕/诊断，不进入 LLM/TTS */
    VISUAL
}
