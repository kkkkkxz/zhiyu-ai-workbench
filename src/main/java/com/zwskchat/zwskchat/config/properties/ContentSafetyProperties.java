package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容安全审核配置。默认启用，使用本地关键词+正则的最小防线；
 * 可通过 zwsk.content-safety.banned-words / banned-patterns 扩展，
 * 或通过切换 {@link com.zwskchat.zwskchat.safety.ContentSafetyService} 实现接入外部审核服务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zwsk.content-safety")
public class ContentSafetyProperties {

    /** 总开关。关闭后审核器退化为全放行（不推荐生产使用）。 */
    private boolean enabled = true;

    /**
     * 审核模式（对应工程计划书“双层防线：轻量分类器 + 模型审核”）：
     * <ul>
     *   <li>{@code keyword}：仅本地关键词+正则（默认，零额外成本、零延迟）；</li>
     *   <li>{@code model}：仅调用 LLM 做内容分类审核；</li>
     *   <li>{@code both}：先过关键词，未命中再过模型审核（关键词作为快速拦截前置，模型兜底语义级风险）。</li>
     * </ul>
     */
    private String mode = "keyword";

    /** 模型审核子配置（mode=model 或 both 时生效）。 */
    private Model model = new Model();

    /** 运维配置的违禁词列表（与代码内置列表合并去重后生效，不区分大小写）。 */
    private List<String> bannedWords = new ArrayList<>();

    /** 运维配置的违禁正则列表。预编译；匹配到即命中。 */
    private List<String> bannedPatterns = new ArrayList<>();

    /** 命中后对用户展示的替换文本。 */
    private String replacement = "抱歉，这个问题我无法回答，可以换一个话题。";

    /** 命中时是否把 reason 记到 WARN 日志。默认 true，便于排查与告警。 */
    private boolean logOnBlock = true;

    /**
     * 模型级内容审核配置。复用现有 chat 模型做一次轻量分类（也可指向独立的 Llama-Guard / Moderation 网关）。
     */
    @Data
    public static class Model {
        /**
         * 待审核文本超过该长度才触发模型审核（短文本由关键词层兜底即可，省成本）。
         * mode=model 时对所有非空文本生效；mode=both 时仅在关键词未命中且超过该长度时触发。
         */
        private int minCharsToInvoke = 0;
        /** 模型审核单次超时（毫秒）。超时按 fail-open 放行，避免拖垮主流程。 */
        private long timeoutMs = 4000;
        /** 模型审核的指令提示词。要求模型只回 SAFE / UNSAFE 及命中类别。 */
        private String prompt = """
                你是企业内容安全分类器。判断【待审文本】是否包含以下任一不安全内容：
                辱骂攻击、歧视仇恨、威胁、色情、暴力、违法犯罪指引、自我伤害诱导、泄露他人隐私、政治敏感越界。
                只输出一行：安全则输出 SAFE；不安全则输出 UNSAFE:<类别>。不要解释，不要复述原文。
                """;
    }
}
