package com.zwskchat.zwskchat.export.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportTaskMessage {

    private Long taskId;
    private Long tenantId;
    private Long userId;
    private String bizType;
    private Long bizId;
    private String format;
}
