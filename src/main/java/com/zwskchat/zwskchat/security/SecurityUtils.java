package com.zwskchat.zwskchat.security;

import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static LoginUser currentUserOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || !(a.getPrincipal() instanceof LoginUser u)) return null;
        return u;
    }

    public static LoginUser currentUser() {
        LoginUser u = currentUserOrNull();
        if (u == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return u;
    }

    public static Long currentUserId() { return currentUser().getUserId(); }

    public static Long currentUserIdOrNull() {
        LoginUser u = currentUserOrNull();
        return u == null ? null : u.getUserId();
    }

    public static Long currentTenantIdOrNull() {
        LoginUser u = currentUserOrNull();
        return u == null ? null : u.getTenantId();
    }
}
