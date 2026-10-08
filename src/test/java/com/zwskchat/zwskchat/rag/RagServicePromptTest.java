package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagServicePromptTest {

    @Test
    void generalPromptRequiresDeepVerticalDomainAnswer() {
        RagService ragService = new RagService(null, null, null, new AiProperties(), null, new HybridFusion());

        List<ChatMessage> prompt = ragService.buildPrompt("ERP 和 RAG 怎么结合落地", List.of());

        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0).getRole()).isEqualTo("system");
        assertThat(prompt.get(0).getContent())
                .contains("业务判断")
                .contains("场景拆解")
                .contains("数据流/接口")
                .contains("权限与租户隔离")
                .contains("验收指标")
                .contains("5 个有信息量的要点");
    }

    @Test
    void ragPromptSeparatesConfirmedFactsAndMethodologyForVerticalDomainAnswer() {
        RagService ragService = new RagService(null, null, null, new AiProperties(), null, new HybridFusion());
        RetrievedChunk chunk = new RetrievedChunk(
                1L,
                2L,
                3L,
                "ERP AI 改造方案",
                "系统包含客户、合同、工单、派发和知识库模块。",
                0.88,
                5);

        List<ChatMessage> prompt = ragService.buildPrompt("如何提升 ERP AI 回答深度", List.of(chunk));

        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0).getRole()).isEqualTo("system");
        assertThat(prompt.get(0).getContent())
                .contains("资料已确认")
                .contains("需要资料确认")
                .contains("业务对象")
                .contains("审计留痕")
                .contains("异常兜底")
                .contains("ERP AI 改造方案")
                .contains("页码：第 5 页");
    }
}
