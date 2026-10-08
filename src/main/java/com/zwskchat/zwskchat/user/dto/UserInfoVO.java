package com.zwskchat.zwskchat.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class UserInfoVO {
    private Long userId;
    private Long tenantId;
    private String tenantCode;
    private String username;
    private String nickname;
    private Set<String> roles;
    private Set<String> permissions;
}
