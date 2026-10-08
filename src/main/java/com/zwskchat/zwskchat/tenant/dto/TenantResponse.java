package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.user.entity.SysTenant;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TenantResponse {

    private Long id;
    private String code;
    private String name;
    private Integer status;
    private String planCode;
    private Integer maxUsers;
    private Long maxStorageMb;
    private LocalDateTime expiresAt;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static TenantResponse from(SysTenant tenant) {
        if (tenant == null) {
            return null;
        }
        return TenantResponse.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .status(tenant.getStatus())
                .planCode(tenant.getPlanCode())
                .maxUsers(tenant.getMaxUsers())
                .maxStorageMb(tenant.getMaxStorageMb())
                .expiresAt(tenant.getExpiresAt())
                .remark(tenant.getRemark())
                .createTime(tenant.getCreateTime())
                .updateTime(tenant.getUpdateTime())
                .build();
    }
}
