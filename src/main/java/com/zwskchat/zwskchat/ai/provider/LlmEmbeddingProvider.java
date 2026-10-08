package com.zwskchat.zwskchat.ai.provider;

import java.util.List;

/**
 * 文本向量化 Provider SPI。
 *   openai-compat → OpenAiCompatibleEmbeddingProvider
 *   dashscope-sdk → DashScopeEmbeddingProvider
 */
public interface LlmEmbeddingProvider {

    String name();

    /** 批量 embed，返回的 vector 顺序与入参一致；失败抛 BusinessException(AI_SERVICE_UNAVAILABLE)。 */
    List<float[]> embed(List<String> texts);
}
