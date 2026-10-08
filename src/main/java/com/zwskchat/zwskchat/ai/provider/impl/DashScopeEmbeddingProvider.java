package com.zwskchat.zwskchat.ai.provider.impl;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.zwskchat.zwskchat.ai.provider.LlmEmbeddingProvider;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云百炼 DashScope SDK 文本向量实现。
 * 推荐模型：text-embedding-v3（1024 维，单次最多 10 条）；text-embedding-v4（多维度可选）。
 * 注意：DashScope Embedding API 限制每次最多处理 10 个文本，超过时自动分批处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingProvider implements LlmEmbeddingProvider {

    private final AiProperties props;
    private final TenantAiConfigService tenantAiConfigService;
    private final TextEmbedding emb = new TextEmbedding();

    @Override
    public String name() { return "dashscope-sdk"; }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) return List.of();
        AiProperties.DashScope ds = props.getDashscope();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                ? tenantConfig.getApiKey()
                : ds.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "DashScope API Key 未配置");
        }
        String model = tenantConfig != null && tenantConfig.getEmbeddingModel() != null
                && !tenantConfig.getEmbeddingModel().isBlank()
                ? tenantConfig.getEmbeddingModel()
                : props.getEmbedding().getModel() != null && !props.getEmbedding().getModel().isBlank()
                ? props.getEmbedding().getModel()
                : (ds.getEmbeddingModel() == null ? "text-embedding-v3" : ds.getEmbeddingModel());

        // DashScope Embedding API 限制：每次最多 10 个文本
        final int BATCH_SIZE = 10;
        List<float[]> allResults = new ArrayList<>(texts.size());

        try {
            for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, texts.size());
                List<String> batch = texts.subList(i, end);

                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .texts(batch)
                        .build();
                TextEmbeddingResult r = emb.call(param);
                if (r == null || r.getOutput() == null || r.getOutput().getEmbeddings() == null) {
                    throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Embedding 返回空");
                }
                List<TextEmbeddingResultItem> items = r.getOutput().getEmbeddings();
                // DashScope 不保证顺序，按 textIndex 排序后返回
                float[][] arr = new float[batch.size()][];
                for (TextEmbeddingResultItem it : items) {
                    int idx = it.getTextIndex() == null ? 0 : it.getTextIndex();
                    List<Double> vec = it.getEmbedding();
                    float[] v = new float[vec.size()];
                    for (int j = 0; j < vec.size(); j++) v[j] = vec.get(j).floatValue();
                    if (idx >= 0 && idx < arr.length) arr[idx] = v;
                }
                for (float[] v : arr) {
                    if (v == null) {
                        throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "Embedding 部分结果缺失");
                    }
                    allResults.add(v);
                }
            }
            return allResults;
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("[DashScope-Embedding] call failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE,
                    "DashScope Embedding 调用失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }
}
