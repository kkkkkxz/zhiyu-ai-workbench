package com.zwskchat.zwskchat.export.service;

import com.zwskchat.zwskchat.export.dto.ExportTaskMessage;

public interface ExportTaskPublisher {

    void publish(ExportTaskMessage message);
}
