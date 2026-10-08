package com.zwskchat.zwskchat.rag.ingest;

import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentMapper;
import com.zwskchat.zwskchat.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Consumes the dead-letter queue produced by {@link IngestListener}.
 *
 * <p>The message is the original {@link IngestMessage} (Jackson-deserialized
 * by the same converter). The DLQ handler:
 * <ul>
 *   <li>marks the document {@code parseStatus = FAILED};</li>
 *   <li>captures the death reason from x-death header into {@code parseError}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestDlqListener {

    private final KnowledgeDocumentMapper documentMapper;

    @RabbitListener(queues = "${zwsk.ingest.dlq:zwsk.ingest.dlq}")
    public void onDeadLetter(IngestMessage msg, Message raw) {
        log.error("[Ingest.DLQ] doc={} tenant={} delivery moved to DLQ", msg.getDocId(), msg.getTenantId());
        try (TenantContext.Scope tenantScope = TenantContext.withTenant(msg.getTenantId())) {
            KnowledgeDocument doc = documentMapper.selectById(msg.getDocId());
            if (doc == null) {
                log.warn("[Ingest.DLQ] doc={} not found, ignore", msg.getDocId());
                return;
            }
            doc.setParseStatus("FAILED");
            doc.setParseError(extractReason(raw));
            documentMapper.updateById(doc);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractReason(Message raw) {
        try {
            var headers = raw.getMessageProperties().getHeaders();
            Object xDeath = headers.get("x-death");
            if (xDeath instanceof java.util.List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> head) {
                Object reason = ((Map<Object, Object>) head).get("reason");
                Object count  = ((Map<Object, Object>) head).get("count");
                Object queue  = ((Map<Object, Object>) head).get("queue");
                return Optional.ofNullable(reason).map(Object::toString).orElse("dead-lettered")
                        + "; count=" + count + "; from=" + queue;
            }
        } catch (Exception ignored) {}
        return "dead-lettered after retry exhaustion";
    }
}
