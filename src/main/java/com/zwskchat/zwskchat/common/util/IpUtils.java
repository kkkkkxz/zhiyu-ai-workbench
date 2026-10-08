package com.zwskchat.zwskchat.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class IpUtils {

    private IpUtils() {}

    private static final String[] HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    public static String getClientIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        for (String h : HEADERS) {
            String ip = req.getHeader(h);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                int idx = ip.indexOf(',');
                return (idx > 0 ? ip.substring(0, idx) : ip).trim();
            }
        }
        return req.getRemoteAddr();
    }
}
