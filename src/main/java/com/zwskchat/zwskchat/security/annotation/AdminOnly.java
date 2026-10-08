package com.zwskchat.zwskchat.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理员专用端点：要求当前登录用户具有 {@code SUPER_ADMIN} 或 {@code TENANT_ADMIN} 角色。
 *
 * <p><b>使用场景：</b>未来新增"系统管理 / 租户后台 / 审计日志"等纯管理性 endpoint 时直接打这个注解。
 * 这样所有"管理员可见"的 controller 共用一个语义入口，将来要扩展（比如加上 {@code KB_ADMIN}）
 * 只需要改这一处的 SpEL 表达式即可，避免散落在各 controller 写一遍 {@code hasAnyRole(...)} 还互相不一致。</p>
 *
 * <p><b>与 {@link com.zwskchat.zwskchat.kb.service.impl.KnowledgeBaseServiceImpl#KB_ADMIN_ROLES KB_ADMIN_ROLES}
 * 的关系：</b>那个白名单是 KB 维度的"读写管理员"（含 KB_ADMIN）；本注解是系统维度的"管理后台"
 * （不含 KB_ADMIN，因为知识库管理员不应该看其他租户用户列表 / 审计日志）。两个集合故意不一样。</p>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * &#64;AdminOnly
 * &#64;GetMapping("/admin/users")
 * public Result&lt;List&lt;UserVO&gt;&gt; listAllUsers() { ... }
 * </pre>
 * </p>
 *
 * <p><b>命名规范提醒：</b>{@code hasAnyRole} 表达式里写的是 {@code 'SUPER_ADMIN'}，不是
 * {@code 'ROLE_SUPER_ADMIN'} —— Spring 会自动加 ROLE_ 前缀。详见
 * {@link com.zwskchat.zwskchat.security.LoginUser#getAuthorities()} 的 javadoc。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
public @interface AdminOnly {
}
