package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.user.entity.SysUserReporting;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReportingRelationResponse {
    private Long id;
    private Long tenantId;
    private Long userId;
    private Long managerUserId;
    private Long deptId;
    private String relationType;
    private LocalDateTime createTime;

    public static ReportingRelationResponse from(SysUserReporting relation) {
        return ReportingRelationResponse.builder()
                .id(relation.getId())
                .tenantId(relation.getTenantId())
                .userId(relation.getUserId())
                .managerUserId(relation.getManagerUserId())
                .deptId(relation.getDeptId())
                .relationType(relation.getRelationType())
                .createTime(relation.getCreateTime())
                .build();
    }
}
