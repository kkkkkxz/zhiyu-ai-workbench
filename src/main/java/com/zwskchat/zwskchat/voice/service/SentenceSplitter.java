package com.zwskchat.zwskchat.voice.service;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 流式输出按句切分器：
 *   - 不断追加 token，遇到中英文句末标点（。！？!?；;）或换行就切出一句；
 *   - 遇到逗号/顿号/冒号等软边界时，在足够长的情况下也切出短语；
 *   - 用于在 LLM 还在生成时就能逐句送入 TTS，从而把首音延迟降到「首句生成时间」。
 *
 * 线程模型：实例上的方法非线程安全，每个 VoiceSession 各自持有一个。
 */
public class SentenceSplitter {

    private static final char[] HARD_BOUNDARY = {'。', '！', '？', '!', '?', '；', ';', '\n'};
    private static final char[] SOFT_BOUNDARY = {'，', ',', '、', '：', ':', '）', ')'};
    private static final int SOFT_FLUSH_MIN = 28;
    private static final int FORCE_FLUSH = 120;
    private final int minLen;
    private final StringBuilder buf = new StringBuilder();

    public SentenceSplitter(int minLen) {
        this.minLen = Math.max(1, minLen);
    }

    /** 追加 LLM 增量；返回本次产出的完整句子列表（可能为空）。 */
    public List<String> append(String delta) {
        List<String> out = new ArrayList<>(2);
        if (delta == null || delta.isEmpty()) return out;
        for (int i = 0; i < delta.length(); i++) {
            char c = delta.charAt(i);
            buf.append(c);
            if (isHardBoundary(c) && buf.length() >= minLen) {
                out.add(buf.toString().trim());
                buf.setLength(0);
            } else if (isSoftBoundary(c) && buf.length() >= Math.max(minLen, SOFT_FLUSH_MIN)) {
                out.add(buf.toString().trim());
                buf.setLength(0);
            } else if (buf.length() >= FORCE_FLUSH && !isAsciiTokenChar(c)) {
                out.add(buf.toString().trim());
                buf.setLength(0);
            }
        }
        return out;
    }

    /** LLM 流结束时调用，flush 缓冲。 */
    public String flush() {
        if (buf.isEmpty()) return null;
        String s = buf.toString().trim();
        buf.setLength(0);
        return s.isEmpty() ? null : s;
    }

    private static boolean isHardBoundary(char c) {
        for (char b : HARD_BOUNDARY) if (b == c) return true;
        return false;
    }

    private static boolean isSoftBoundary(char c) {
        for (char b : SOFT_BOUNDARY) if (b == c) return true;
        return false;
    }

    private static boolean isAsciiTokenChar(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '#' || c == '+' || c == '.';
    }
}
