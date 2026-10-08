package com.zwskchat.zwskchat.chat;

import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.chat.mapper.AiMessageReferenceMapper;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.chat.service.SessionService;
import com.zwskchat.zwskchat.chat.service.impl.ChatServiceImpl;
import com.zwskchat.zwskchat.config.properties.PersonaProperties;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import com.zwskchat.zwskchat.persona.service.PersonaService;
import com.zwskchat.zwskchat.rag.RagService;
import com.zwskchat.zwskchat.rag.confidence.ConfidenceScoringService;
import com.zwskchat.zwskchat.rag.confidence.ResponseGovernanceService;
import com.zwskchat.zwskchat.metrics.RagMetricsRecorder;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.safety.ContentSafetyService;
import com.zwskchat.zwskchat.stock.service.StockDialogueService;
import com.zwskchat.zwskchat.ai.hub.IntentRouter;
import com.zwskchat.zwskchat.ai.hub.TextToSqlEngine;
import com.zwskchat.zwskchat.ai.hub.TenantSafeSchemaProvider;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceImplPromptTest {

    @Test
    void mergesSelectedPersonaIntoPrimarySystemPrompt() throws Exception {
        RagService ragService = mock(RagService.class);
        AiMessageMapper messageMapper = mock(AiMessageMapper.class);
        when(ragService.buildPrompt(eq("你是什么角色的"), any()))
                .thenReturn(List.of(
                        ChatMessage.system("你是固定企业知识助手。"),
                        ChatMessage.user("你是什么角色的")));
        when(messageMapper.selectList(any())).thenReturn(List.of());

        ChatServiceImpl service = new ChatServiceImpl(
                mock(SessionService.class),
                mock(KnowledgeBaseService.class),
                ragService,
                new ConfidenceScoringService(new AiProperties()),
                new ResponseGovernanceService(new AiProperties()),
                new RagMetricsRecorder(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                mock(AiChatClient.class),
                mock(AiSessionMapper.class),
                messageMapper,
                mock(AiMessageReferenceMapper.class),
                mock(ContentSafetyService.class),
                mock(StockDialogueService.class),
                new PersonaService(new PersonaProperties()),
                mock(IntentRouter.class),
                mock(TextToSqlEngine.class),
                mock(TenantSafeSchemaProvider.class),
                new AiProperties(),
                mock(ThreadPoolTaskExecutor.class));

        Method method = ChatServiceImpl.class.getDeclaredMethod(
                "buildPromptWithHistory",
                Long.class,
                Long.class,
                String.class,
                List.class,
                String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ChatMessage> prompt = (List<ChatMessage>) method.invoke(
                service,
                1L,
                2L,
                "你是什么角色的",
                List.of(),
                "onboarding_trainer");

        assertThat(prompt)
                .filteredOn(message -> "system".equals(message.getRole()))
                .hasSize(1);
        assertThat(prompt.get(0).getRole()).isEqualTo("system");
        assertThat(prompt.get(0).getContent())
                .contains("你是固定企业知识助手。")
                .contains("新员工培训员")
                .contains("优先回答当前选择的人设名称");
        assertThat(prompt.get(prompt.size() - 1))
                .usingRecursiveComparison()
                .isEqualTo(ChatMessage.user("你是什么角色的"));
    }
}
