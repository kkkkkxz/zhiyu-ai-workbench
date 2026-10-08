package com.zwskchat.zwskchat.safety;

import com.zwskchat.zwskchat.config.properties.ContentSafetyProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 默认的内容安全审核实现：本地关键词 + 正则。
 *
 * <p><b>作用对象是 LLM 的对外输出</b>，不是用户输入。命中即用配置的"礼貌拒绝"文本替换整段，
 * 调用方（VoiceOrchestratorV3 / ChatServiceImpl）再决定是 TTS 出拒绝文案还是直接中止后续生成。</p>
 *
 * <p>注意事项：
 * <ul>
 *     <li>本实现是"最小防线"，用于对抗最典型的 prompt-injection 改写和最明显的辱骂。
 *         真实生产强一致审核请替换为外部 API 实现。</li>
 *     <li>关键词匹配为子串匹配、忽略大小写、忽略 ASCII 空白差异。</li>
 *     <li>任何异常都被吞掉并降级为 pass()，避免审核层本身把业务打挂。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordContentSafetyService implements ContentSafetyService {

    private final ContentSafetyProperties props;

    /**
     * 代码内置的最小违禁词清单。这些词是安全过滤器的"检测目标"（而非建议输出）。
     * 选取原则：
     * 1) 极少在合法企业知识库/客服对话中出现；
     * 2) 出现时基本等同于模型被 jailbreak 或输出攻击性内容；
     * 3) 全部小写后做子串匹配。
     * 运维可通过 {@code zwsk.content-safety.banned-words} 追加行业或公司定制词。
     */
    private static final List<String> BUILTIN_BANNED_WORDS = List.of(
            // —— prompt-injection 改写标记（输出里出现说明被越权诱导）——
            "ignore previous instructions",
            "ignore all previous",
            "disregard the above",
            "你现在扮演",
            "忽略上面的指令",
            "忘记之前的指令",
            // —— 最常见的辱骂/攻击词（兜底；完整脏话库请通过配置补充）——
            "傻逼",
            "sb你",
            "操你妈",
            "草你妈",
            "cnmb",
            "fuck you"
    );

    /** 归一化后的最终生效关键词列表。 */
    private volatile List<String> normalizedWords = List.of();

    /** 预编译的违禁正则。 */
    private volatile List<Pattern> compiledPatterns = List.of();

    @PostConstruct
    void init() {
        List<String> merged = new ArrayList<>(BUILTIN_BANNED_WORDS.size() + props.getBannedWords().size());
        for (String w : BUILTIN_BANNED_WORDS) {
            String n = normalize(w);
            if (!n.isEmpty() && !merged.contains(n)) merged.add(n);
        }
        for (String w : props.getBannedWords()) {
            String n = normalize(w);
            if (!n.isEmpty() && !merged.contains(n)) merged.add(n);
        }
        this.normalizedWords = List.copyOf(merged);

        List<Pattern> ps = new ArrayList<>(props.getBannedPatterns().size());
        for (String raw : props.getBannedPatterns()) {
            if (raw == null || raw.isBlank()) continue;
            try {
                ps.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL));
            } catch (PatternSyntaxException e) {
                log.warn("[ContentSafety] invalid banned-pattern ignored: '{}', err={}", raw, e.getMessage());
            }
        }
        this.compiledPatterns = List.copyOf(ps);

        log.info("[ContentSafety] enabled={} builtin={} custom-words={} custom-patterns={}",
                props.isEnabled(),
                BUILTIN_BANNED_WORDS.size(),
                props.getBannedWords().size(),
                compiledPatterns.size());
    }

    @Override
    public SafetyResult check(String text) {
        try {
            if (!props.isEnabled() || text == null || text.isBlank()) {
                return SafetyResult.pass(text);
            }
            String normalized = normalize(text);

            for (String w : normalizedWords) {
                if (normalized.contains(w)) {
                    return block("keyword:" + preview(w));
                }
            }
            for (Pattern p : compiledPatterns) {
                if (p.matcher(text).find()) {
                    return block("regex:" + preview(p.pattern()));
                }
            }
            return SafetyResult.pass(text);
        } catch (Exception e) {
            // fail-open：审核层自身不应拖垮业务
            log.warn("[ContentSafety] check threw, fall through as pass: {}", e.getMessage());
            return SafetyResult.pass(text);
        }
    }

    private SafetyResult block(String reason) {
        if (props.isLogOnBlock()) {
            log.warn("[ContentSafety] blocked by {} replacement='{}'", reason, props.getReplacement());
        }
        return SafetyResult.block(reason, props.getReplacement());
    }

    /**
     * 标准化文本：小写 + 去除 ASCII 空白 & 零宽字符，用于绕过"操 你 妈"之类的轻量对抗。
     * 对中文字符本身不改动（大小写无意义），不做深入的简繁转换/同音替换，避免误伤合法内容。
     */
    private static String normalize(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') continue;
            // 常见零宽字符
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    private static String preview(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 32 ? t.substring(0, 32) + "…" : t;
    }
}
