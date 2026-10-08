package com.zwskchat.zwskchat.voice.asr;

public interface AsrSession {
    /** 推送音频数据（PCM 16bit 16kHz mono） */
    void sendAudio(byte[] pcm);
    /** 用户说完，触发最终识别 */
    void endOfStream();
    /** 取消并释放资源 */
    void cancel();
}
