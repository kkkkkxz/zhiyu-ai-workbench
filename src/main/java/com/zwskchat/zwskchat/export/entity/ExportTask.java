package com.zwskchat.zwskchat.export.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("export_task")
public class ExportTask {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String bizType;
    private Long bizId;
    private String format;
    private String status;
    private String fileKey;
    private String fileName;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
