package com.zwskchat.zwskchat.ai.hub.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.ai.hub.BizActionResult;
import com.zwskchat.zwskchat.ai.hub.BizActionType;
import com.zwskchat.zwskchat.ai.hub.entity.AiActionExecutionLog;

import java.util.Map;

public interface AiActionAuditService {

    void recordPreview(BizActionType actionType, String userInput, BizActionResult result);

    void recordExecution(BizActionType actionType, Map<String, Object> previewData, BizActionResult result);

    IPage<AiActionExecutionLog> listMyLogs(Page<AiActionExecutionLog> page);
}
