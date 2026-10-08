package com.zwskchat.zwskchat.voice.asr;

public interface AsrCallback {
    /** 中间结果（可选实现） */
    default void onPartial(String text) {}
    /** 最终结果 */
    void onFinal(String text);
    /** 错误 */
    void onError(Throwable t);
}
