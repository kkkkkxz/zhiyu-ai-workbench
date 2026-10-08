package com.zwskchat.zwskchat.safety;

import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.ModelTier;
import com.zwskchat.zwskchat.ai.ModelTierContext;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.config.properties.ContentSafetyProperties;
import com.zwskchat.zwskchat.safety.ContentSafetyService.SafetyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 模型级内容审核（对应工程计划书“双层防线：轻量分类器/Moderation + 主模型”）。
 *
 * <p>复用 {@link AiChatClient}（可指向独立的 Llama-Guard / Moderation 网关）对一段对外输出做语义级分类，
 * 弥补关键词层无法覆盖的“换着花样说”的攻击性/违规内容。审核走 LIGHT 档模型，控制成本与延迟。</p>
 *
 * <p>契约同 {@link ContentSafetyService}：永不抛异常，任何异常/超时都 fail-open 放行，
 * 避免审核层自身把业务打挂。该类不直接实现 {@link ContentSafetyService} 接口，
 * 由 {@link ContentSafetyDispatcher} 统一编排，避免出现多个同类型 Bean 注入歧义。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelContentSafetyService {

    private final ContentSafetyProperties props;
    private final AiChatClient chatClient;

    /**
     * 模型审核一段文本。返回 null 表示“无法判定/未触发”，调用方据此决定是否放行。
     */
    public SafetyResult check(String text) {
        if (text == null || text.isBlank()) {
            return SafetyResult.pass(text);
        }
        ContentSafetyProperties.Model cfg = props.getModel();
        if (text.length() < Math.max(0, cfg.getMinCharsToInvoke())) {
            // 短文本不值得调用模型，交回上层（通常已有关键词层兜底）。
            return SafetyResult.pass(text);
        }
        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try (ModelTierContext.Scope ignored = ModelTierContext.with(ModelTier.LIGHT)) {
                    StringBuilder sb = new StringBuilder();
                    chatClient.streamChat(
                            List.of(ChatMessage.system(cfg.getPrompt()),
                                    ChatMessage.user("【待审文本】\n" + text)),
                            sb::append,
                            null);
                    return sb.toString();
                }
            });
            String verdict = future.get(Math.max(500, cfg.getTimeoutMs()), TimeUnit.MILLISECONDS);
            if (verdict == null) return SafetyResult.pass(text);
            String norm = verdict.trim().toUpperCase();
            if (norm.contains("UNSAFE")) {
                String reason = "model:" + norm.replaceAll("\\s+", " ");
                if (props.isLogOnBlock()) {
                    log.warn("[ContentSafety] model blocked, verdict='{}'", truncate(norm));
                }
                return SafetyResult.block(reason.length() > 64 ? reason.substring(0, 64) : reason,
                        props.getReplacement());
            }
            return SafetyResult.pass(text);
        } catch (Exception e) {
            // fail-open：模型审核不稳定时不应拖垮业务主流程。
            log.warn("[ContentSafety] model check failed, fall through as pass: {}", e.getMessage());
            return SafetyResult.pass(text);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
