package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.tenant.entity.AdminApply;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminApplyResponse {
    private Long id;
    private Long tenantId;
    private Long userId;
    private String applyType;
    private String targetRole;
    private String reason;
    private String status;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String rejectReason;
    private LocalDateTime createTime;

    public static AdminApplyResponse from(AdminApply apply) {
        return AdminApplyResponse.builder()
                .id(apply.getId())
                .tenantId(apply.getTenantId())
                .userId(apply.getUserId())
                .applyType(apply.getApplyType())
                .targetRole(apply.getTargetRole())
                .reason(apply.getReason())
                .status(apply.getStatus())
                .reviewerId(apply.getReviewerId())
                .reviewTime(apply.getReviewTime())
                .rejectReason(apply.getRejectReason())
                .createTime(apply.getCreateTime())
                .build();
    }
}
