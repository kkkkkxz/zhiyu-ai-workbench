package com.zwskchat.zwskchat.auth.dto;

import com.zwskchat.zwskchat.user.entity.SysTenant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantOptionResponse {
    private Long id;
    private String code;
    private String name;

    public static TenantOptionResponse from(SysTenant tenant) {
        return TenantOptionResponse.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .build();
    }
}
