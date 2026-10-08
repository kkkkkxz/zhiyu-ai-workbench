package com.zwskchat.zwskchat.voice.tts;

public interface TtsProvider {

    String name();

    /** 输出音频格式（mp3 / opus / pcm 等） */
    String audioFormat();

    /** 异步合成；调用方需保留返回的 task 句柄以便 cancel */
    TtsTask synthesize(String text, TtsCallback callback);
}
