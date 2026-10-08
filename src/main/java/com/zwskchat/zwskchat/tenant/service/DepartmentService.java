package com.zwskchat.zwskchat.tenant.service;

import com.zwskchat.zwskchat.tenant.dto.*;

import java.util.List;

public interface DepartmentService {

    List<DepartmentResponse> tree(Long tenantId);

    DepartmentResponse create(Long tenantId, CreateDepartmentRequest request);

    DepartmentResponse update(Long tenantId, Long deptId, UpdateDepartmentRequest request);

    void delete(Long tenantId, Long deptId);

    void assignUser(Long tenantId, Long userId, Long deptId);

    List<ReportingRelationResponse> listReporting(Long tenantId, Long userId);

    ReportingRelationResponse addReporting(Long tenantId, ReportingRelationRequest request);

    void removeReporting(Long tenantId, Long relationId);
}
