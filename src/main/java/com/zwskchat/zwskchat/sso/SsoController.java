package com.zwskchat.zwskchat.sso;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.auth.dto.LoginResponse;
import com.zwskchat.zwskchat.auth.service.AuthService;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import com.zwskchat.zwskchat.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部单点登录（SSO）：自包含实现，不依赖外部 IdP。
 *
 * <p>流程：已登录用户调 {@code POST /sso/ticket} 取一次性 ticket（短期有效）→
 * 携带 ticket 跳到其它子系统/客户端 → 子系统调 {@code POST /sso/exchange} 用 ticket
 * 换取本系统 JWT，实现“一次登录、多处免登”。ticket 一次性消费、短 TTL。</p>
 */
@Tag(name = "单点登录 SSO")
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoTicketService ssoTicketService;
    private final AuthService authService;

    @Operation(summary = "签发一次性 SSO 票据（需已登录）")
    @PostMapping("/ticket")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(key = "sso:ticket", limit = 20, window = 60)
    @AuditLog(operationType = "SSO_ISSUE_TICKET", targetType = "user")
    public Result<Map<String, Object>> issueTicket() {
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        Long userId = SecurityUtils.currentUserId();
        String ticket = ssoTicketService.issue(tenantId, userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticket", ticket);
        data.put("expiresInSeconds", SsoTicketService.TTL_SECONDS);
        return Result.success(data);
    }

    @Operation(summary = "用 SSO 票据换取本系统 JWT（匿名可调）")
    @PostMapping("/exchange")
    @RateLimit(key = "sso:exchange", limit = 30, window = 60, dimension = RateLimit.Dimension.IP)
    @AuditLog(operationType = "SSO_EXCHANGE", targetType = "user")
    public Result<LoginResponse> exchange(@RequestBody @Valid SsoExchangeRequest req,
                                          HttpServletRequest http) {
        return Result.success(authService.ssoExchange(req.getTicket(), http));
    }
}
