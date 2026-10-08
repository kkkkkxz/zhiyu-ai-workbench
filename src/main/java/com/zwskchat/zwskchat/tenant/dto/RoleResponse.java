package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.user.entity.SysRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleResponse {

    private Long id;
    private Long tenantId;
    private String roleCode;
    private String roleName;
    private String description;

    public static RoleResponse from(SysRole role) {
        if (role == null) {
            return null;
        }
        return RoleResponse.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .build();
    }
}
