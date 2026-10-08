package com.zwskchat.zwskchat.ai.usage.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.ai.usage.entity.AiModelUsageLog;
import com.zwskchat.zwskchat.ai.usage.mapper.AiModelUsageLogMapper;
import com.zwskchat.zwskchat.ai.usage.service.AiModelUsageService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelUsageServiceImpl implements AiModelUsageService {

    private final AiModelUsageLogMapper usageMapper;

    @Async
    @Override
    public void record(String provider, String model, String usageType,
                       int promptTokens, int completionTokens, long latencyMs,
                       String bizType, String bizAction, String status, String errorMsg) {
        try {
            LoginUser user = SecurityUtils.currentUserOrNull();
            AiModelUsageLog usageLog = new AiModelUsageLog();
            usageLog.setTenantId(user != null ? user.getTenantId() : 0L);
            usageLog.setUserId(user != null ? user.getUserId() : 0L);
            usageLog.setProvider(provider);
            usageLog.setModel(model);
            usageLog.setUsageType(usageType);
            usageLog.setPromptTokens(promptTokens);
            usageLog.setCompletionTokens(completionTokens);
            usageLog.setTotalTokens(promptTokens + completionTokens);
            usageLog.setLatencyMs((int) latencyMs);
            usageLog.setBizType(bizType);
            usageLog.setBizAction(bizAction);
            usageLog.setStatus(status != null ? status : "SUCCESS");
            usageLog.setErrorMsg(errorMsg);
            usageMapper.insert(usageLog);
        } catch (Exception e) {
            log.warn("[AiModelUsage] record failed model={} err={}", model, e.getMessage());
        }
    }

    @Override
    public IPage<AiModelUsageLog> listByTenant(Page<AiModelUsageLog> page) {
        LoginUser user = SecurityUtils.currentUser();
        return usageMapper.selectPage(page, Wrappers.<AiModelUsageLog>lambdaQuery()
                .eq(AiModelUsageLog::getTenantId, user.getTenantId())
                .orderByDesc(AiModelUsageLog::getCreateTime));
    }

    @Override
    public Map<String, Object> tenantSummary() {
        LoginUser user = SecurityUtils.currentUser();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);

        List<AiModelUsageLog> todayLogs = usageMapper.selectList(
                Wrappers.<AiModelUsageLog>lambdaQuery()
                        .eq(AiModelUsageLog::getTenantId, user.getTenantId())
                        .ge(AiModelUsageLog::getCreateTime, todayStart));

        long totalTokens = 0;
        long totalCalls = todayLogs.size();
        long failedCalls = 0;
        for (AiModelUsageLog l : todayLogs) {
            totalTokens += l.getTotalTokens() != null ? l.getTotalTokens() : 0;
            if ("FAILED".equals(l.getStatus()) || "TIMEOUT".equals(l.getStatus())) {
                failedCalls++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("todayTotalTokens", totalTokens);
        result.put("todayTotalCalls", totalCalls);
        result.put("todayFailedCalls", failedCalls);
        result.put("todaySuccessRate", totalCalls > 0 ? (double) (totalCalls - failedCalls) / totalCalls * 100 : 100.0);
        return result;
    }
}
