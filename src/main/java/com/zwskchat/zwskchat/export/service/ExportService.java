package com.zwskchat.zwskchat.export.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.export.entity.ExportTask;
import com.zwskchat.zwskchat.export.dto.ExportDownloadResponse;

public interface ExportService {
    ExportTask createTask(String bizType, Long bizId, String format);
    void processTask(Long taskId);
    ExportTask getById(Long id);
    ExportDownloadResponse createDownloadUrl(Long id);
    IPage<ExportTask> myTasks(Page<ExportTask> page);
}
