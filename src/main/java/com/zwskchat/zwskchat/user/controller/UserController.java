package com.zwskchat.zwskchat.user.controller;

import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.user.dto.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户", description = "当前用户信息")
@RestController
@RequestMapping("/users")
// /users/me 必须登录后才能取，否则 SecurityUtils.currentUser() 会抛 UNAUTHORIZED
@org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
public class UserController {

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        LoginUser u = SecurityUtils.currentUser();
        return Result.success(UserInfoVO.builder()
                .userId(u.getUserId())
                .tenantId(u.getTenantId())
                .tenantCode(u.getTenantCode())
                .username(u.getUsername())
                .roles(u.getRoles())
                .permissions(u.getPermissions())
                .build());
    }
}
