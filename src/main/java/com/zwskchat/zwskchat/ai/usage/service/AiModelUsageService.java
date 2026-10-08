package com.zwskchat.zwskchat.ai.usage.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.ai.usage.entity.AiModelUsageLog;

import java.util.Map;

public interface AiModelUsageService {

    void record(String provider, String model, String usageType,
                int promptTokens, int completionTokens, long latencyMs,
                String bizType, String bizAction, String status, String errorMsg);

    IPage<AiModelUsageLog> listByTenant(Page<AiModelUsageLog> page);

    Map<String, Object> tenantSummary();
}
