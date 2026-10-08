package com.zwskchat.zwskchat.ai.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.provider.LlmEmbeddingProvider;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embeddings 接口实现。
 * POST {baseUrl}/embeddings
 * 请求体：{"model": "...", "input": ["..."], "encoding_format": "float"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingProvider implements LlmEmbeddingProvider {

    private final AiProperties props;
    private final TenantAiConfigService tenantAiConfigService;
    private final ObjectMapper om;
    private HttpClient http;

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String name() { return "openai-compat"; }

    @Override
    public List<float[]> embed(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        AiProperties.Embedding cfg = props.getEmbedding();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = valueOrDefault(tenantConfig == null ? null : tenantConfig.getApiKey(), cfg.getApiKey());
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Embedding API Key 未配置");
        }
        String baseUrl = valueOrDefault(tenantConfig == null ? null : tenantConfig.getBaseUrl(), cfg.getBaseUrl());
        String model = valueOrDefault(tenantConfig == null ? null : tenantConfig.getEmbeddingModel(), cfg.getModel());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", inputs);
            body.put("encoding_format", "float");

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl.replaceAll("/+$", "") + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[Embedding] non-2xx status={} body={}", resp.statusCode(), truncate(resp.body()));
                throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Embedding HTTP " + resp.statusCode());
            }
            JsonNode root = om.readTree(resp.body());
            JsonNode dataArr = root.path("data");
            List<float[]> result = new ArrayList<>(inputs.size());
            for (int i = 0; i < dataArr.size(); i++) {
                JsonNode arr = dataArr.get(i).path("embedding");
                float[] v = new float[arr.size()];
                for (int j = 0; j < arr.size(); j++) v[j] = (float) arr.get(j).asDouble();
                result.add(v);
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Embedding] call failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Embedding 调用失败");
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
