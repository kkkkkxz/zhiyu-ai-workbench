package com.zwskchat.zwskchat.voice.service;

import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 唤醒词检测器（对应工程计划书“唤醒词层建议独立”）。
 *
 * <p>判断一段 ASR 文本是否以配置的唤醒词开头，并支持剥离唤醒词前缀。
 * always-on 唤醒优先由端侧本地引擎（Porcupine 等）完成，本类是服务端的二次确认 / 兜底。
 * 纯函数、无外部依赖，便于单元测试。</p>
 */
@Component
public class WakeWordDetector {

    /** 检测结果：是否唤醒、剥离唤醒词后的有效文本、命中的唤醒词。 */
    public record Result(boolean awake, String strippedText, String matchedPhrase) {
    }

    /**
     * 检测唤醒词。
     *
     * @param text 原始 ASR 文本
     * @param cfg  唤醒词配置
     * @return 命中时 awake=true 并给出（按需）剥离前缀后的文本；未命中 awake=false。
     */
    public Result detect(String text, VoiceProperties.WakeWord cfg) {
        if (text == null) return new Result(false, "", null);
        String original = text.trim();
        if (cfg == null || cfg.getPhrases() == null || cfg.getPhrases().isEmpty()) {
            // 未配置唤醒词等于不设门槛，直接放行。
            return new Result(true, original, null);
        }
        String normText = normalize(original);
        if (normText.isEmpty()) return new Result(false, "", null);

        for (String phrase : cfg.getPhrases()) {
            String normPhrase = normalize(phrase);
            if (normPhrase.isEmpty()) continue;
            if (normText.startsWith(normPhrase)) {
                String stripped = cfg.isStripPrefix()
                        ? stripPrefix(original, phrase)
                        : original;
                return new Result(true, stripped, phrase);
            }
        }
        return new Result(false, original, null);
    }

    /**
     * 从原文中剥离唤醒词前缀：用归一化方式定位前缀长度，再在原文上跳过等量的有效字符，
     * 同时吃掉紧跟其后的标点/空白（“你好展望，今天…” → “今天…”）。
     */
    private static String stripPrefix(String original, String phrase) {
        String normPhrase = normalize(phrase);
        if (normPhrase.isEmpty()) return original;
        int matched = 0;
        int i = 0;
        for (; i < original.length() && matched < normPhrase.length(); i++) {
            char c = normalizeChar(original.charAt(i));
            if (isMeaningful(c)) {
                matched++;
            }
        }
        // 跳过紧随其后的分隔符（逗号、空格、语气词标点等）
        while (i < original.length() && !isMeaningful(normalizeChar(original.charAt(i)))) {
            i++;
        }
        String rest = original.substring(Math.min(i, original.length())).trim();
        return rest.isEmpty() ? original : rest;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = normalizeChar(raw.charAt(i));
            if (isMeaningful(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static char normalizeChar(char c) {
        if (c == '　') return ' ';
        if (c >= '！' && c <= '～') c = (char) (c - 0xFEE0);
        return Character.toLowerCase(c);
    }

    private static boolean isMeaningful(char c) {
        return Character.isLetterOrDigit(c);
    }

    /** 便捷重载：使用给定短语列表（用于不经配置对象的场景/测试）。 */
    public Result detect(String text, List<String> phrases, boolean stripPrefix) {
        VoiceProperties.WakeWord cfg = new VoiceProperties.WakeWord();
        cfg.setPhrases(phrases);
        cfg.setStripPrefix(stripPrefix);
        return detect(text, cfg);
    }
}
