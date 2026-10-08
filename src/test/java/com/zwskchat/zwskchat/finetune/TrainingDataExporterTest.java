package com.zwskchat.zwskchat.finetune;

import com.zwskchat.zwskchat.chat.entity.AiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingDataExporterTest {

    private final TrainingDataExporter exporter = new TrainingDataExporter();

    private AiMessage msg(String role, String content, Integer feedback) {
        AiMessage m = new AiMessage();
        m.setRole(role);
        m.setContent(content);
        m.setFeedback(feedback);
        return m;
    }

    @Test
    void pairsUserThenAssistant() {
        List<SftSample> samples = exporter.pairMessages(List.of(
                msg("user", "差旅报销流程是什么", null),
                msg("assistant", "提交发票、部门审批、财务复核、付款。", null)
        ), "你是企业知识助手", false);

        assertEquals(1, samples.size());
        assertEquals("你是企业知识助手", samples.get(0).system());
        assertEquals("差旅报销流程是什么", samples.get(0).user());
        assertTrue(samples.get(0).assistant().contains("财务复核"));
    }

    @Test
    void usesInlineSystemMessageOverDefault() {
        List<SftSample> samples = exporter.pairMessages(List.of(
                msg("system", "自定义角色", null),
                msg("user", "问题", null),
                msg("assistant", "回答", null)
        ), "默认角色", false);

        assertEquals("自定义角色", samples.get(0).system());
    }

    @Test
    void onlyPositiveKeepsLikedAnswersOnly() {
        List<SftSample> samples = exporter.pairMessages(List.of(
                msg("user", "问题1", null),
                msg("assistant", "差答案", -1),
                msg("user", "问题2", null),
                msg("assistant", "好答案", 1)
        ), null, true);

        assertEquals(1, samples.size());
        assertEquals("问题2", samples.get(0).user());
        assertEquals("好答案", samples.get(0).assistant());
    }

    @Test
    void skipsAssistantWithoutPrecedingUser() {
        List<SftSample> samples = exporter.pairMessages(List.of(
                msg("assistant", "孤立回答", null),
                msg("user", "问题", null),
                msg("assistant", "回答", null)
        ), null, false);

        assertEquals(1, samples.size());
        assertEquals("问题", samples.get(0).user());
    }

    @Test
    void jsonlIsValidShareGptLines() {
        String jsonl = exporter.toJsonl(List.of(
                new SftSample("系统", "用户问\"带引号\"", "助手答\n换行")));

        assertTrue(jsonl.endsWith("\n"));
        // JSON 转义正确：引号与换行被转义，不会破坏单行 JSONL
        assertTrue(jsonl.contains("\\\"带引号\\\""));
        assertTrue(jsonl.contains("\\n"));
        assertEquals(1, jsonl.split("\n").length);
        assertTrue(jsonl.contains("\"role\":\"system\""));
        assertTrue(jsonl.contains("\"role\":\"assistant\""));
    }

    @Test
    void emptyInputsAreSafe() {
        assertTrue(exporter.pairMessages(null, null, false).isEmpty());
        assertEquals("", exporter.toJsonl(List.of()));
    }

    @Test
    void omitsSystemWhenBlank() {
        String jsonl = exporter.toJsonl(List.of(new SftSample(null, "q", "a")));
        assertFalse(jsonl.contains("\"role\":\"system\""));
        assertTrue(jsonl.contains("\"role\":\"user\""));
    }
}
