package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.hub.PresalesTaskDomain;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PresalesOutputContractTest {

    private RagService newRagService(AiProperties props) {
        return new RagService(null, null, null, props, null, new HybridFusion());
    }

    @Test
    void injectsOutputContractForSolutionDraftingWithoutChunks() {
        List<ChatMessage> prompt = newRagService(new AiProperties())
                .buildPrompt("帮我草拟一版企业知识助手方案", List.of());

        assertThat(prompt).hasSize(2);
        String system = prompt.get(0).getContent();
        assertThat(system)
                .contains("输出契约")
                .contains("需求判断")
                .contains("匹配方案")
                .contains("可复用案例")
                .contains("风险与边界")
                .contains("建议下一步动作")
                .contains("依据来源")
                .contains("企业方案库 > 企业案例库");
    }

    @Test
    void injectsOutputContractForNeedMatchingWithChunks() {
        RetrievedChunk chunk = new RetrievedChunk(1L, 2L, 3L, "案例库",
                "某科研院所知识助手案例。", 0.9, 1);
        List<ChatMessage> prompt = newRagService(new AiProperties())
                .buildPrompt("我们是研究院，想要一套智能问答助手", List.of(chunk));

        assertThat(prompt).hasSize(2);
        String system = prompt.get(0).getContent();
        // 既包含原有 KB 证据上下文，也包含追加的输出契约
        assertThat(system)
                .contains("参考资料")
                .contains("案例库")
                .contains("输出契约")
                .contains("建议下一步动作");
    }

    @Test
    void doesNotInjectContractForGeneralChitchat() {
        List<ChatMessage> prompt = newRagService(new AiProperties())
                .buildPrompt("你好呀", List.of());

        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0).getContent()).doesNotContain("输出契约");
    }

    @Test
    void respectsDisabledPresalesSwitch() {
        AiProperties props = new AiProperties();
        props.getPresales().setEnabled(false);

        List<ChatMessage> prompt = newRagService(props)
                .buildPrompt("帮我草拟一版方案", List.of());

        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0).getContent()).doesNotContain("输出契约");
    }

    @Test
    void respectsDisabledOutputContractSwitch() {
        AiProperties props = new AiProperties();
        props.getPresales().setOutputContractEnabled(false);

        List<ChatMessage> prompt = newRagService(props)
                .buildPrompt("帮我草拟一版方案", List.of());

        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0).getContent()).doesNotContain("输出契约");
    }

    @Test
    void contractBlockEmptyForMaterialQueryDomain() {
        // 资料查询是确定性信息查询，不套用结构化输出契约
        assertThat(PresalesPromptComposer.contractBlock(PresalesTaskDomain.MATERIAL_QUERY)).isEmpty();
        assertThat(PresalesPromptComposer.contractBlock(PresalesTaskDomain.GENERAL)).isEmpty();
        assertThat(PresalesPromptComposer.contractBlock(PresalesTaskDomain.SOLUTION_DRAFTING)).isNotEmpty();
    }
}
