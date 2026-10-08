package com.zwskchat.zwskchat.ai.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.ModelTierContext;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.provider.LlmChatProvider;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 Chat Completions 流式实现。
 * 兼容：OpenAI、DashScope-Compatible HTTP、DeepSeek、Moonshot、智谱、Ollama OpenAI mode 等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatProvider implements LlmChatProvider {

    private static final double MAX_CHAT_TEMPERATURE = 0.15D;

    private final AiProperties props;
    private final TenantAiConfigService tenantAiConfigService;
    private final ObjectMapper om;
    private HttpClient http;

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String name() { return "openai-compat"; }

    @Override
    public String activeModel() {
        AiProperties.Chat cfg = props.getChat();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String baseModel = valueOrDefault(tenantConfig == null ? null : tenantConfig.getChatModel(), cfg.getModel());
        return props.resolveTierModel(ModelTierContext.current(), baseModel);
    }

    @Override
    public String streamChat(List<ChatMessage> messages,
                             Consumer<String> onDelta,
                             Consumer<AiChatClient.UsageInfo> onUsage) {
        AiProperties.Chat cfg = props.getChat();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = valueOrDefault(tenantConfig == null ? null : tenantConfig.getApiKey(), cfg.getApiKey());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Chat API Key 未配置");
        }
        String baseUrl = valueOrDefault(tenantConfig == null ? null : tenantConfig.getBaseUrl(), cfg.getBaseUrl());
        String baseModel = valueOrDefault(tenantConfig == null ? null : tenantConfig.getChatModel(), cfg.getModel());
        // 分层路由：按当前任务档位（LIGHT/STANDARD/STRONG）选择实际模型，未开启时等于 baseModel。
        String model = props.resolveTierModel(ModelTierContext.current(), baseModel);
        double temperature = clampTemperature(tenantConfig != null && tenantConfig.getTemperature() != null
                ? tenantConfig.getTemperature()
                : cfg.getTemperature());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", temperature);
            body.put("max_tokens", props.getMaxOutputTokens());
            body.put("stream", true);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(props.getChatTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("[Chat] non-2xx status={} body={}", resp.statusCode(), truncate(err));
                throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Chat HTTP " + resp.statusCode());
            }

            StringBuilder full = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) break;
                    try {
                        JsonNode node = om.readTree(payload);
                        JsonNode choice0 = node.path("choices").path(0);
                        String delta = choice0.path("delta").path("content").asText("");
                        if (!delta.isEmpty()) {
                            full.append(delta);
                            if (onDelta != null) onDelta.accept(delta);
                        }
                        JsonNode usageNode = node.path("usage");
                        if (!usageNode.isMissingNode() && !usageNode.isNull() && onUsage != null) {
                            AiChatClient.UsageInfo u = new AiChatClient.UsageInfo(
                                    usageNode.path("prompt_tokens").asInt(0),
                                    usageNode.path("completion_tokens").asInt(0),
                                    usageNode.path("total_tokens").asInt(0));
                            onUsage.accept(u);
                        }
                    } catch (Exception parseEx) {
                        log.debug("[Chat] parse line failed: {}", parseEx.getMessage());
                    }
                }
            }
            return full.toString();
        } catch (BusinessException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException te) {
            throw new BusinessException(ResultCode.AI_TIMEOUT);
        } catch (Exception e) {
            log.error("[Chat] call failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Chat 调用失败");
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static double clampTemperature(double value) {
        if (!Double.isFinite(value)) return 0.1D;
        return Math.max(0D, Math.min(value, MAX_CHAT_TEMPERATURE));
    }
}
