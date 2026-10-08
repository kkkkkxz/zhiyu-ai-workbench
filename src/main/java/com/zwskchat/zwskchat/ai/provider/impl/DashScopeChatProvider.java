package com.zwskchat.zwskchat.ai.provider.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.ModelTierContext;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.provider.LlmChatProvider;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import io.reactivex.Flowable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 阿里云百炼 DashScope SDK 流式对话实现：
 *   底层走原生 DashScope HTTP/WS 协议（增量输出），
 *   无需 OpenAI 兼容层，token 计数（input/output/total）原生提供。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeChatProvider implements LlmChatProvider {

    private static final double MAX_CHAT_TEMPERATURE = 0.15D;

    private final AiProperties props;
    private final TenantAiConfigService tenantAiConfigService;
    private final Generation gen = new Generation();

    @Override
    public String name() { return "dashscope-sdk"; }

    @Override
    public String activeModel() {
        return props.resolveTierModel(ModelTierContext.current(), resolveBaseModel());
    }

    /** 解析基础模型：租户运行时配置 > chat.model > dashscope.chatModel > qwen-plus。 */
    private String resolveBaseModel() {
        AiProperties.DashScope ds = props.getDashscope();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        if (tenantConfig != null && tenantConfig.getChatModel() != null && !tenantConfig.getChatModel().isBlank()) {
            return tenantConfig.getChatModel();
        }
        if (props.getChat().getModel() != null && !props.getChat().getModel().isBlank()) {
            return props.getChat().getModel();
        }
        return ds.getChatModel() == null ? "qwen-plus" : ds.getChatModel();
    }

    @Override
    public String streamChat(List<ChatMessage> messages,
                             Consumer<String> onDelta,
                             Consumer<AiChatClient.UsageInfo> onUsage) {
        AiProperties.DashScope ds = props.getDashscope();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                ? tenantConfig.getApiKey()
                : ds.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "DashScope API Key 未配置");
        }
        // 分层路由：按当前任务档位选择实际模型，未开启时等于基础模型。
        String model = props.resolveTierModel(ModelTierContext.current(), resolveBaseModel());
        double temperature = clampTemperature(tenantConfig != null && tenantConfig.getTemperature() != null
                ? tenantConfig.getTemperature()
                : props.getChat().getTemperature());

        List<Message> sdkMessages = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            String role = mapRole(m.getRole());
            sdkMessages.add(Message.builder().role(role).content(m.getContent()).build());
        }

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(sdkMessages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .temperature((float) temperature)
                .maxTokens(props.getMaxOutputTokens())
                .build();

        StringBuilder full = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<AiChatClient.UsageInfo> usageRef = new AtomicReference<>();
        try {
            Flowable<GenerationResult> flow = gen.streamCall(param);
            flow.blockingForEach(r -> {
                if (r == null || r.getOutput() == null
                        || r.getOutput().getChoices() == null
                        || r.getOutput().getChoices().isEmpty()) return;
                String delta = r.getOutput().getChoices().get(0).getMessage().getContent();
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    if (onDelta != null) onDelta.accept(delta);
                }
                if (r.getUsage() != null) {
                    usageRef.set(new AiChatClient.UsageInfo(
                            nz(r.getUsage().getInputTokens()),
                            nz(r.getUsage().getOutputTokens()),
                            nz(r.getUsage().getTotalTokens())));
                }
            });
        } catch (Exception e) {
            if (isCancellation(e)) {
                if (hasInterruptedCause(e)) {
                    Thread.currentThread().interrupt();
                }
                log.info("[DashScope-Chat] streaming cancelled: {}", safeMsg(e));
                throw new CancellationException("__interrupted__");
            }
            errorRef.set(e);
        }

        if (errorRef.get() != null) {
            log.error("[DashScope-Chat] streaming failed", errorRef.get());
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE,
                    "DashScope 对话失败：" + safeMsg(errorRef.get()));
        }
        if (onUsage != null && usageRef.get() != null) onUsage.accept(usageRef.get());
        return full.toString();
    }

    private static String mapRole(String role) {
        if (role == null) return Role.USER.getValue();
        return switch (role) {
            case "system" -> Role.SYSTEM.getValue();
            case "assistant" -> Role.ASSISTANT.getValue();
            case "tool" -> Role.TOOL.getValue();
            default -> Role.USER.getValue();
        };
    }

    private static int nz(Integer v) { return v == null ? 0 : v; }

    private static boolean isCancellation(Throwable t) {
        if (Thread.currentThread().isInterrupted()) return true;
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof InterruptedException || cur instanceof CancellationException) return true;
            if ("__interrupted__".equals(cur.getMessage())) return true;
        }
        return false;
    }

    private static boolean hasInterruptedCause(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof InterruptedException) return true;
        }
        return false;
    }

    private static String safeMsg(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static double clampTemperature(double value) {
        if (!Double.isFinite(value)) return 0.1D;
        return Math.max(0D, Math.min(value, MAX_CHAT_TEMPERATURE));
    }
}
