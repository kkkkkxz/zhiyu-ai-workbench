package com.zwskchat.zwskchat.finetune;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练数据导出器（对应工程计划书“模型微调”章节的“产出可训练数据”职责）。
 *
 * <p>把会话消息按 user→assistant 配对成 SFT 样本，并序列化为 ShareGPT 风格 JSONL：
 * <pre>{"messages":[{"role":"system",...},{"role":"user",...},{"role":"assistant",...}]}</pre>
 * 支持只导出获得正向反馈（点赞）的高质量问答，作为微调精料。纯函数逻辑，便于单元测试。
 */
@Slf4j
@Component
public class TrainingDataExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 把按时间升序排列的会话消息配对为 SFT 样本。
     *
     * @param chronological     同一会话内按 createTime 升序的消息
     * @param defaultSystem     无显式 system 消息时使用的默认 system prompt（可空）
     * @param onlyPositive      是否仅保留 assistant 获得正向反馈（feedback &gt; 0）的样本
     */
    public List<SftSample> pairMessages(List<AiMessage> chronological,
                                        String defaultSystem,
                                        boolean onlyPositive) {
        List<SftSample> samples = new ArrayList<>();
        if (chronological == null || chronological.isEmpty()) return samples;

        String system = defaultSystem;
        AiMessage lastUser = null;
        for (AiMessage msg : chronological) {
            if (msg == null || msg.getRole() == null) continue;
            switch (msg.getRole()) {
                case "system" -> {
                    if (msg.getContent() != null && !msg.getContent().isBlank()) {
                        system = msg.getContent();
                    }
                }
                case "user" -> lastUser = msg;
                case "assistant" -> {
                    if (lastUser == null) break;
                    boolean positive = msg.getFeedback() != null && msg.getFeedback() > 0;
                    if (onlyPositive && !positive) {
                        lastUser = null;
                        break;
                    }
                    if (isUsable(lastUser.getContent()) && isUsable(msg.getContent())) {
                        samples.add(new SftSample(system, lastUser.getContent().trim(),
                                msg.getContent().trim()));
                    }
                    lastUser = null;
                }
                default -> { /* 忽略其它角色 */ }
            }
        }
        return samples;
    }

    /** 序列化为 ShareGPT 风格 JSONL（每行一个样本）。 */
    public String toJsonl(List<SftSample> samples) {
        if (samples == null || samples.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (SftSample s : samples) {
            String line = toJsonLine(s);
            if (line != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String toJsonLine(SftSample s) {
        List<Map<String, String>> messages = new ArrayList<>(3);
        if (s.system() != null && !s.system().isBlank()) {
            messages.add(message("system", s.system()));
        }
        messages.add(message("user", s.user()));
        messages.add(message("assistant", s.assistant()));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("messages", messages);
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("[Finetune] serialize sample failed: {}", e.getMessage());
            return null;
        }
    }

    private static Map<String, String> message(String role, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static boolean isUsable(String s) {
        return s != null && !s.isBlank();
    }
}
