package com.zwskchat.zwskchat.ai.provider;

import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * 聊天 LLM Provider SPI。多个实现按 zwsk.ai.chat.provider 路由：
 *   openai-compat → OpenAiCompatibleChatProvider
 *   dashscope-sdk → DashScopeChatProvider
 */
public interface LlmChatProvider {

    /** 与配置 zwsk.ai.chat.provider 匹配的标识 */
    String name();

    /**
     * 当前生效的模型标识（结合租户运行时配置与全局默认解析得到），用于回答回溯与用量记账。
     * 实现应返回与本次 streamChat 实际使用一致的 model 名称。
     */
    String activeModel();

    /** 流式调用，逐 token 通过 onDelta 回调；usageCallback 在结束时返回用量。 */
    String streamChat(List<ChatMessage> messages,
                      Consumer<String> onDelta,
                      Consumer<AiChatClient.UsageInfo> usageCallback);
}
