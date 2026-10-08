package com.zwskchat.zwskchat.common.api;

import lombok.Getter;

/**
 * 全局错误码。区间约定：
 * 1xxx 通用 / 2xxx 认证授权 / 3xxx 用户租户 / 4xxx 知识库 / 5xxx 会话 / 6xxx AI / 7xxx 文件 / 8xxx 限流安全 / 9xxx 系统
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),

    BAD_REQUEST(1001, "请求参数不合法"),
    NOT_FOUND(1002, "资源不存在"),
    METHOD_NOT_ALLOWED(1003, "请求方法不允许"),
    INTERNAL_ERROR(1004, "系统内部错误"),
    BUSINESS_ERROR(1005, "业务异常"),

    UNAUTHORIZED(2001, "未登录或登录已过期"),
    FORBIDDEN(2002, "无访问权限"),
    LOGIN_FAILED(2003, "用户名或密码错误"),
    ACCOUNT_LOCKED(2004, "账号已锁定，请稍后再试"),
    ACCOUNT_DISABLED(2005, "账号已停用"),
    TOKEN_INVALID(2006, "Token 无效"),
    TOKEN_EXPIRED(2007, "Token 已过期"),
    TOKEN_REVOKED(2008, "Token 已注销"),

    USER_NOT_FOUND(3001, "用户不存在"),
    USERNAME_EXISTS(3002, "用户名已存在"),
    TENANT_CODE_REQUIRED(3003, "用户名在多个租户中存在，请提供租户编码"),
    TENANT_NOT_FOUND(3004, "租户不存在或已停用"),

    KB_NOT_FOUND(4001, "知识库不存在"),
    KB_NO_PERMISSION(4002, "无知识库访问权限"),
    DOC_PARSE_FAILED(4003, "文档解析失败"),

    AI_SERVICE_UNAVAILABLE(6001, "AI 服务暂时不可用"),
    AI_TIMEOUT(6002, "AI 服务响应超时"),
    PROMPT_INJECTION_DETECTED(6003, "检测到非法指令，已拦截"),

    FILE_TYPE_NOT_ALLOWED(7001, "文件类型不允许"),
    FILE_TOO_LARGE(7002, "文件大小超出限制"),
    FILE_UPLOAD_FAILED(7003, "文件上传失败"),

    RATE_LIMITED(8001, "请求过于频繁，请稍后再试"),
    SENSITIVE_CONTENT(8002, "请求或回答包含敏感内容"),

    DEPENDENCY_UNAVAILABLE(9001, "依赖服务不可用");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
