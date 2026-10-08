package com.zwskchat.zwskchat.audit.service.impl;

import com.zwskchat.zwskchat.audit.entity.AuditLogEntity;
import com.zwskchat.zwskchat.audit.mapper.AuditLogMapper;
import com.zwskchat.zwskchat.audit.service.AuditLogService;
import com.zwskchat.zwskchat.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditMapper;

    @Async("auditExecutor")
    @Override
    public void asyncSave(AuditLogEntity entity) {
        // 审计跨租户读，但写入时不应被租户拦截器影响（审计表带 tenant_id，由调用方填充）
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            auditMapper.insert(entity);
        } catch (Exception e) {
            log.error("[Audit] save failed", e);
        }
    }
}
