package com.zwskchat.zwskchat.common.constant;

public final class SecurityConstants {

    private SecurityConstants() {}

    public static final String HEADER_AUTH = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_TENANT_CODE = "tcode";
    public static final String CLAIM_USERNAME = "name";
    public static final String CLAIM_TOKEN_ID = "jti";
    public static final String CLAIM_TOKEN_TYPE = "typ";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TENANT_HEADER = "X-Tenant-Id";
}
