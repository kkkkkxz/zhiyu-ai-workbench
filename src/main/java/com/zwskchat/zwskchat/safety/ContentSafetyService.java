package com.zwskchat.zwskchat.safety;

/**
 * 内容安全审核抽象。
 *
 * <p>所有对外输出的文本在通过 TTS 合成 / SSE 推送 / 落库之前，都应先过一遍该门禁。
 * 当前默认实现为本地关键词+正则（{@link KeywordContentSafetyService}），
 * 后续可插入外部审核（如阿里云内容安全、OpenAI Moderation）作为同一接口的实现。</p>
 *
 * <p>契约：
 * <ul>
 *     <li>{@link #check(String)} <b>永不抛</b>异常；任何异常都应被实现类吞掉并返回 pass()（fail-open）。
 *         原因：审核服务自身不稳定时不应拖垮业务主流程。</li>
 *     <li>{@link SafetyResult#sanitized()} 不为 null。safe=true 时等同入参，safe=false 时为"礼貌拒绝"替换文本。</li>
 * </ul>
 */
public interface ContentSafetyService {

    /**
     * 审核一段文本。输入为 null / 空串时直接 pass。
     */
    SafetyResult check(String text);

    /**
     * @param safe 是否通过审核
     * @param reason 命中规则（仅用于日志/告警，不回传用户）
     * @param sanitized 对用户可见的文本：safe=true 时等同原文；safe=false 时为替换文本
     */
    record SafetyResult(boolean safe, String reason, String sanitized) {

        public static SafetyResult pass(String text) {
            return new SafetyResult(true, null, text == null ? "" : text);
        }

        public static SafetyResult block(String reason, String replacement) {
            return new SafetyResult(false, reason, replacement == null ? "" : replacement);
        }
    }
}
