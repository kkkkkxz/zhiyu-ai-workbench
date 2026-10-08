package com.zwskchat.zwskchat.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long accessExpiresAt;
    private long refreshExpiresAt;
    private String tokenType;
    private Long userId;
    private String username;
    private String nickname;
    private Long tenantId;
    private String tenantCode;
    private Set<String> roles;
    private Set<String> permissions;
}
