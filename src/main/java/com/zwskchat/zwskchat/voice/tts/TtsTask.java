package com.zwskchat.zwskchat.voice.tts;

public interface TtsTask {
    /** 取消并释放资源（用于客户端 INTERRUPT） */
    void cancel();
    /** 是否已完成 */
    boolean isDone();
}
