package com.zwskchat.zwskchat.finetune;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 训练数据服务：从会话消息中提取 SFT 样本并导出 JSONL。
 * 租户隔离由 MyBatis-Plus 租户拦截器自动保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingDataService {

    private final AiMessageMapper messageMapper;
    private final TrainingDataExporter exporter;

    /**
     * 导出 SFT JSONL。
     *
     * @param sessionId     仅导出指定会话；为空则导出当前租户全部会话
     * @param onlyPositive  是否仅导出获得点赞的高质量问答
     * @param defaultSystem 默认 system prompt（会话内无 system 消息时使用）
     */
    public String exportSftJsonl(Long sessionId, boolean onlyPositive, String defaultSystem) {
        var query = Wrappers.<AiMessage>lambdaQuery()
                .orderByAsc(AiMessage::getSessionId)
                .orderByAsc(AiMessage::getCreateTime);
        if (sessionId != null) {
            query.eq(AiMessage::getSessionId, sessionId);
        }
        List<AiMessage> all = messageMapper.selectList(query);

        // 按会话分组后在每个会话内部配对，避免跨会话错配
        Map<Long, List<AiMessage>> bySession = new LinkedHashMap<>();
        for (AiMessage m : all) {
            bySession.computeIfAbsent(m.getSessionId(), k -> new ArrayList<>()).add(m);
        }

        List<SftSample> samples = new ArrayList<>();
        for (List<AiMessage> msgs : bySession.values()) {
            samples.addAll(exporter.pairMessages(msgs, defaultSystem, onlyPositive));
        }
        log.info("[Finetune] export SFT sessions={}, samples={}, onlyPositive={}",
                bySession.size(), samples.size(), onlyPositive);
        return exporter.toJsonl(samples);
    }
}
