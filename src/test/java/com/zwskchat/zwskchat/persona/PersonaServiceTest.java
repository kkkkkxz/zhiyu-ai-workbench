package com.zwskchat.zwskchat.persona;

import com.zwskchat.zwskchat.config.properties.PersonaProperties;
import com.zwskchat.zwskchat.persona.service.PersonaService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersonaServiceTest {

    @Test
    void listsTheEightBuiltInPersonas() {
        PersonaService service = new PersonaService(new PersonaProperties());

        assertThat(service.listEnabled())
                .extracting("id")
                .containsExactly(
                        "general",
                        "receptionist",
                        "onboarding_trainer",
                        "process_consultant",
                        "product_briefing",
                        "support_specialist",
                        "tech_support",
                        "exec_briefing");
    }

    @Test
    void injectsSelectedPersonaPromptAndFallsBackToGeneral() {
        PersonaService service = new PersonaService(new PersonaProperties());

        assertThat(service.systemPrompt("general"))
                .contains("行业 AI 知识助手")
                .contains("RAG 应用")
                .contains("不要出现公司头衔或公司介绍");

        assertThat(service.systemPrompt("missing"))
                .contains("行业 AI 知识助手");
    }
}
