package com.zwskchat.zwskchat.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {

    /** 操作类型，如 AUTH_LOGIN / KB_DOC_UPLOAD / KB_DOC_DELETE */
    String operationType();

    /** 操作对象类型，如 user / kb / document */
    String targetType() default "";

    /** SpEL 表达式，从入参/返回值解析 targetId，例如 "#req.userId" */
    String targetIdExpr() default "";

    /** 是否记录请求体（注意脱敏，敏感接口建议 false） */
    boolean recordArgs() default false;
}
