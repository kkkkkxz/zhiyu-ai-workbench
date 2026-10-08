package com.zwskchat.zwskchat.export.consumer;

import com.zwskchat.zwskchat.config.properties.ExportProperties;
import com.zwskchat.zwskchat.export.dto.ExportTaskMessage;
import com.zwskchat.zwskchat.export.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskListener {

    private final ExportService exportService;

    @RabbitListener(queues = "${zwsk.export.queue:zwsk.export.queue}")
    public void onMessage(ExportTaskMessage message) {
        log.info("[Export] consume task id={} bizType={} bizId={} format={}",
                message.getTaskId(), message.getBizType(), message.getBizId(), message.getFormat());
        exportService.processTask(message.getTaskId());
    }
}
