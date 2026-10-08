package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.export")
public class ExportProperties {

    private String exchange = "zwsk.export.exchange";
    private String queue = "zwsk.export.queue";
    private String routingKey = "zwsk.export";
    private String dlqExchange = "zwsk.export.dlx";
    private String dlq = "zwsk.export.dlq";
    private String dlqRoutingKey = "zwsk.export.dlq";
    private final Retry retry = new Retry();

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long initialIntervalMs = 1000;
        private double multiplier = 2.0;
        private long maxIntervalMs = 10000;
    }
}
