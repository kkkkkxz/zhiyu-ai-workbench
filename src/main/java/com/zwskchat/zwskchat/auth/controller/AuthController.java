package com.zwskchat.zwskchat.auth.controller;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.auth.dto.LoginRequest;
import com.zwskchat.zwskchat.auth.dto.LoginResponse;
import com.zwskchat.zwskchat.auth.dto.RefreshTokenRequest;
import com.zwskchat.zwskchat.auth.dto.RegisterRequest;
import com.zwskchat.zwskchat.auth.dto.TenantOptionResponse;
import com.zwskchat.zwskchat.auth.service.AuthService;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.common.constant.SecurityConstants;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.user.entity.SysTenant;
import com.zwskchat.zwskchat.user.mapper.SysTenantMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "登录 / 刷新 / 注销")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysTenantMapper tenantMapper;

    @Operation(summary = "注册可选企业列表")
    @GetMapping("/tenants")
    public Result<java.util.List<TenantOptionResponse>> tenants() {
        try (TenantContext.Scope ignored = TenantContext.ignore()) {
            java.util.List<TenantOptionResponse> tenants = tenantMapper.selectList(
                            Wrappers.<SysTenant>lambdaQuery()
                                    .eq(SysTenant::getStatus, 1)
                                    .ne(SysTenant::getCode, "platform")
                                    .orderByAsc(SysTenant::getId))
                    .stream()
                    .map(TenantOptionResponse::from)
                    .toList();
            return Result.success(tenants);
        }
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @RateLimit(key = "auth:login", limit = 10, window = 60, dimension = RateLimit.Dimension.IP)
    @AuditLog(operationType = "AUTH_LOGIN", targetType = "user")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest req, HttpServletRequest http) {
        return Result.success(authService.login(req, http));
    }

    @Operation(summary = "用户自助注册")
    @PostMapping("/register")
    // 限流维度按 IP 而不是用户：注册时还没用户身份；3 次/分钟足够正常用户使用，
    // 同时阻止脚本批量注册压库。recordArgs=false 避免明文密码进审计日志。
    @RateLimit(key = "auth:register", limit = 3, window = 60, dimension = RateLimit.Dimension.IP)
    @AuditLog(operationType = "AUTH_REGISTER", targetType = "user", recordArgs = false)
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest req, HttpServletRequest http) {
        return Result.success(authService.register(req, http));
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/refresh")
    @RateLimit(key = "auth:refresh", limit = 30, window = 60, dimension = RateLimit.Dimension.IP)
    public Result<LoginResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return Result.success(authService.refresh(req.getRefreshToken()));
    }

    @Operation(summary = "注销")
    @PostMapping("/logout")
    // 注销必须有有效 token 才能撤销 jti；匿名调 logout 没意义且会让攻击者用脚本探测
    // "哪些 access token jti 当前还有效"。注意 AuthController 不能加类级 isAuthenticated，
    // 因为 login / register / refresh 必须允许匿名（permit-urls 已放行 + method-level
    // 不限制），所以本端点单独声明。
    @PreAuthorize("isAuthenticated()")
    @AuditLog(operationType = "AUTH_LOGOUT", targetType = "user")
    public Result<Void> logout(@RequestHeader(name = SecurityConstants.HEADER_AUTH, required = false) String authHeader) {
        authService.logout(authHeader);
        return Result.success();
    }
}
