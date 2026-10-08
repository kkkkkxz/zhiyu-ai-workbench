package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Async document indexing pipeline (M1.1).
 *
 * When {@code async.enabled = true}, document uploads go through:
 *   1. Persist {@code knowledge_document} row with state = PENDING.
 *   2. Publish {@link com.zwskchat.zwskchat.rag.ingest.IngestMessage}
 *      to {@code zwsk.ingest.exchange} with routing key {@code zwsk.ingest.routing-key}.
 *   3. Listener consumes from {@code zwsk.ingest.queue} and runs IndexingService.
 *   4. After {@code retry.max-attempts} failures, message is rejected and routed to DLQ.
 *
 * Setting {@code async.enabled = false} falls back to synchronous indexing,
 * useful when RabbitMQ is unavailable.
 */
@Data
@Component
@ConfigurationProperties(prefix = "zwsk.ingest")
public class IngestProperties {

    private final Async async = new Async();
    private String exchange = "zwsk.ingest.exchange";
    private String queue = "zwsk.ingest.queue";
    private String routingKey = "zwsk.ingest";
    private String dlqExchange = "zwsk.ingest.dlx";
    private String dlq = "zwsk.ingest.dlq";
    private String dlqRoutingKey = "zwsk.ingest.dlq";
    private final Retry retry = new Retry();

    @Data
    public static class Async {
        /** When false, uploadText falls back to synchronous indexing. */
        private boolean enabled = true;
    }

    @Data
    public static class Retry {
        /** Spring Retry: total attempts including the first try. */
        private int maxAttempts = 3;
        /** Initial back-off in milliseconds. */
        private long initialIntervalMs = 1000;
        /** Multiplier between attempts. */
        private double multiplier = 2.0;
        /** Cap for back-off interval. */
        private long maxIntervalMs = 10000;
    }
}
