package com.zwskchat.zwskchat.ai.hub.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.hub.BizActionResult;
import com.zwskchat.zwskchat.ai.hub.BizActionType;
import com.zwskchat.zwskchat.ai.hub.entity.AiActionExecutionLog;
import com.zwskchat.zwskchat.ai.hub.mapper.AiActionExecutionLogMapper;
import com.zwskchat.zwskchat.ai.hub.service.AiActionAuditService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiActionAuditServiceImpl implements AiActionAuditService {

    private static final String PHASE_PREVIEW = "PREVIEW";
    private static final String PHASE_EXECUTE = "EXECUTE";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String RISK_LOW = "LOW";
    private static final int MAX_INPUT_LENGTH = 1000;

    private final AiActionExecutionLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void recordPreview(BizActionType actionType, String userInput, BizActionResult result) {
        LoginUser user = SecurityUtils.currentUserOrNull();
        if (user == null) {
            return;
        }
        AiActionExecutionLog log = baseLog(user, actionType, PHASE_PREVIEW);
        log.setUserInput(limit(userInput));
        log.setPreviewJson(toJson(result));
        log.setStatus(result != null && result.getActionType() != BizActionType.UNKNOWN ? STATUS_SUCCESS : STATUS_FAILED);
        log.setErrorMessage(result != null && result.getActionType() == BizActionType.UNKNOWN ? result.getSummary() : null);
        insertQuietly(log);
    }

    @Override
    public void recordExecution(BizActionType actionType, Map<String, Object> previewData, BizActionResult result) {
        LoginUser user = SecurityUtils.currentUserOrNull();
        if (user == null) {
            return;
        }
        AiActionExecutionLog log = baseLog(user, actionType, PHASE_EXECUTE);
        log.setPreviewJson(toJson(previewData));
        log.setResultJson(toJson(result));
        log.setStatus(result != null && result.isExecuted() ? STATUS_SUCCESS : STATUS_FAILED);
        log.setErrorMessage(result != null && !result.isExecuted() ? result.getSummary() : null);
        insertQuietly(log);
    }

    @Override
    public IPage<AiActionExecutionLog> listMyLogs(Page<AiActionExecutionLog> page) {
        LoginUser user = SecurityUtils.currentUser();
        return logMapper.selectPage(page, Wrappers.<AiActionExecutionLog>lambdaQuery()
                .eq(AiActionExecutionLog::getUserId, user.getUserId())
                .orderByDesc(AiActionExecutionLog::getCreateTime));
    }

    private AiActionExecutionLog baseLog(LoginUser user, BizActionType actionType, String phase) {
        AiActionExecutionLog log = new AiActionExecutionLog();
        log.setTenantId(user.getTenantId());
        log.setUserId(user.getUserId());
        log.setActionCode(actionType == null ? BizActionType.UNKNOWN.name() : actionType.name());
        log.setActionName(actionDisplayName(actionType));
        log.setPhase(phase);
        log.setRiskLevel(RISK_LOW);
        log.setTraceId(MDC.get("traceId"));
        return log;
    }

    private String actionDisplayName(BizActionType actionType) {
        if (actionType == null) {
            return "未知动作";
        }
        return switch (actionType) {
            case DISPATCH -> "派发任务";
            case CREATE_CUSTOMER -> "创建客户";
            case CREATE_CONTRACT -> "创建合同";
            case CREATE_WORKORDER -> "创建工单";
            case CREATE_SCHEDULE -> "创建日程";
            case START_APPROVAL -> "发起审批";
            case QUERY_CUSTOMER -> "查询客户";
            case QUERY_CONTRACT -> "查询合同";
            case QUERY_WORKORDER -> "查询工单";
            case QUERY_SCHEDULE -> "查询日程";
            case ADD_FOLLOW_UP -> "添加跟进";
            case UNKNOWN -> "未知动作";
        };
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{\"serialization\":\"failed\"}";
        }
    }

    private String limit(String input) {
        if (input == null || input.length() <= MAX_INPUT_LENGTH) {
            return input;
        }
        return input.substring(0, MAX_INPUT_LENGTH);
    }

    private void insertQuietly(AiActionExecutionLog actionLog) {
        try {
            logMapper.insert(actionLog);
        } catch (RuntimeException e) {
            log.warn("[AiActionAudit] insert failed action={} phase={} err={}",
                    actionLog.getActionCode(), actionLog.getPhase(), e.getMessage());
        }
    }
}
