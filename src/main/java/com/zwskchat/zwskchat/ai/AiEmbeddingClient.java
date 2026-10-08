package com.zwskchat.zwskchat.ai;

import com.zwskchat.zwskchat.ai.provider.LlmEmbeddingProvider;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文本向量化路由器：根据 zwsk.ai.embedding.provider 选择具体实现。
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class AiEmbeddingClient {

    private final AiProperties props;
    private final List<LlmEmbeddingProvider> providers;
    private final TenantAiConfigService tenantAiConfigService;
    private Map<String, LlmEmbeddingProvider> byName;

    @PostConstruct
    void init() {
        this.byName = providers.stream()
                .collect(Collectors.toMap(p -> p.name().toLowerCase(), p -> p, (a, b) -> a));
        log.info("[AI] embedding providers loaded: {} → active={}", byName.keySet(), props.getEmbedding().getProvider());
    }

    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        return resolve().embed(inputs);
    }

    private LlmEmbeddingProvider resolve() {
        String want = tenantAiConfigService.getCurrentRuntimeConfig()
                .map(config -> config.getProvider())
                .filter(provider -> provider != null && !provider.isBlank())
                .orElse(props.getEmbedding().getProvider());
        if (want == null || want.isBlank()) {
            want = "openai-compat";
        }
        LlmEmbeddingProvider p = byName.get(want.toLowerCase());
        if (p == null) {
            log.warn("[AI] embedding provider '{}' not found, fallback to openai-compat. available={}", want, byName.keySet());
            p = byName.get("openai-compat");
        }
        if (p == null) throw new IllegalStateException("没有可用的 LlmEmbeddingProvider");
        return p;
    }
}
