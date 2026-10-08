package com.zwskchat.zwskchat.rag.ingest;

import com.zwskchat.zwskchat.config.properties.IngestProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link IngestMessage} to the ingest exchange.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestProducer {

    private final RabbitTemplate rabbitTemplate;
    private final IngestProperties ingestProps;

    /** Inline 路径：M1.1 兼容，消息体直接带文本。 */
    public void publish(Long docId, Long tenantId, String content) {
        String traceId = MDC.get("traceId");
        IngestMessage msg = IngestMessage.inline(docId, tenantId, content, traceId);
        rabbitTemplate.convertAndSend(
                ingestProps.getExchange(),
                ingestProps.getRoutingKey(),
                msg);
        log.info("[Ingest] published(inline) doc={} tenant={} bytes={} trace={}",
                docId, tenantId, content == null ? 0 : content.length(), traceId);
    }

    /** Object 路径：M2，消息只带 objectKey，listener 自己从 MinIO 拉流并 Tika 解析。 */
    public void publishObject(Long docId, Long tenantId, String objectKey, String fileName) {
        String traceId = MDC.get("traceId");
        IngestMessage msg = IngestMessage.object(docId, tenantId, objectKey, fileName, traceId);
        rabbitTemplate.convertAndSend(
                ingestProps.getExchange(),
                ingestProps.getRoutingKey(),
                msg);
        log.info("[Ingest] published(object) doc={} tenant={} objectKey={} trace={}",
                docId, tenantId, objectKey, traceId);
    }
}
