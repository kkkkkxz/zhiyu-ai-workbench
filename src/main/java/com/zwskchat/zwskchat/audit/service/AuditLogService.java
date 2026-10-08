package com.zwskchat.zwskchat.audit.service;

import com.zwskchat.zwskchat.audit.entity.AuditLogEntity;

public interface AuditLogService {
    void asyncSave(AuditLogEntity entity);
}
