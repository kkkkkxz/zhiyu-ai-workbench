package com.zwskchat.zwskchat.tenant.dto;

import com.zwskchat.zwskchat.user.entity.SysUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class TenantUserResponse {

    private Long id;
    private Long tenantId;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private Set<String> roles;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static TenantUserResponse from(SysUser user, Set<String> roles) {
        if (user == null) {
            return null;
        }
        return TenantUserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(roles)
                .lastLoginTime(user.getLastLoginTime())
                .lastLoginIp(user.getLastLoginIp())
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }
}
