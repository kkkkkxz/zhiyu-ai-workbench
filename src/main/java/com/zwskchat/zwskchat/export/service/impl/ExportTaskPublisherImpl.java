package com.zwskchat.zwskchat.export.service.impl;

import com.zwskchat.zwskchat.config.properties.ExportProperties;
import com.zwskchat.zwskchat.export.dto.ExportTaskMessage;
import com.zwskchat.zwskchat.export.service.ExportTaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskPublisherImpl implements ExportTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ExportProperties exportProperties;

    @Override
    public void publish(ExportTaskMessage message) {
        rabbitTemplate.convertAndSend(
                exportProperties.getExchange(),
                exportProperties.getRoutingKey(),
                message);
        log.info("[Export] published task id={} bizType={} bizId={} format={}",
                message.getTaskId(), message.getBizType(), message.getBizId(), message.getFormat());
    }
}
