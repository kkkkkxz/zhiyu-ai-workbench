package com.zwskchat.zwskchat.voice.tts;

public interface TtsCallback {
    /** 音频片段（格式由 audioFormat 决定，例如 mp3 / opus / pcm） */
    void onAudio(byte[] audio);
    /** 全部音频生成完毕 */
    void onComplete();
    /** 错误 */
    void onError(Throwable t);
}
