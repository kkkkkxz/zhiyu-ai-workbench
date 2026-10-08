package com.zwskchat.zwskchat.voice.asr;

public interface AsrProvider {

    /** 提供商标识，与 zwsk.voice.asr-provider 配置匹配 */
    String name();

    /** 打开一个新的 ASR 会话 */
    AsrSession open(int sampleRate, AsrCallback callback);
}
