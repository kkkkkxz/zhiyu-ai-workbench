package com.zwskchat.zwskchat.ai;

import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.provider.LlmChatProvider;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 聊天 LLM 路由器：根据 zwsk.ai.chat.provider 选择具体实现。
 * 调用方仍然依赖 AiChatClient 类型，因此现有 RagService / ChatServiceImpl / VoiceOrchestratorV3 不需要改。
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class AiChatClient {

    private final AiProperties props;
    private final List<LlmChatProvider> providers;
    private final TenantAiConfigService tenantAiConfigService;
    private Map<String, LlmChatProvider> byName;

    @PostConstruct
    void init() {
        this.byName = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(), p -> p, (a, b) -> a));
        log.info("[AI] chat providers loaded: {} → active={}", byName.keySet(), props.getChat().getProvider());
    }

    public String streamChat(List<ChatMessage> messages,
                             Consumer<String> onDelta,
                             Consumer<UsageInfo> onUsage) {
        return resolve().streamChat(messages, onDelta, onUsage);
    }

    /** 当前生效的 chat 模型标识（结合 provider 路由 + 租户配置 + 分层档位），用于回答回溯与记账。 */
    public String activeModel() {
        try {
            return resolve().activeModel();
        } catch (Exception e) {
            log.debug("[AI] activeModel resolve failed: {}", e.getMessage());
            return null;
        }
    }

    private LlmChatProvider resolve() {
        String want = tenantAiConfigService.getCurrentRuntimeConfig()
                .map(config -> config.getProvider())
                .filter(provider -> provider != null && !provider.isBlank())
                .orElse(props.getChat().getProvider());
        if (want == null || want.isBlank()) {
            want = "openai-compat";
        }
        LlmChatProvider p = byName.get(want.toLowerCase());
        if (p == null) {
            log.warn("[AI] chat provider '{}' not found, fallback to openai-compat. available={}", want, byName.keySet());
            p = byName.get("openai-compat");
        }
        if (p == null) throw new IllegalStateException("没有可用的 LlmChatProvider");
        return p;
    }

    /** 历史调用方仍然 import com.zwskchat.zwskchat.ai.AiChatClient.UsageInfo，保持不动。 */
    public record UsageInfo(int promptTokens, int completionTokens, int totalTokens) {}
}
