package com.zwskchat.zwskchat.auth.service;

import com.zwskchat.zwskchat.auth.dto.LoginRequest;
import com.zwskchat.zwskchat.auth.dto.LoginResponse;
import com.zwskchat.zwskchat.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    LoginResponse login(LoginRequest req, HttpServletRequest httpReq);

    /**
     * 用户自助注册。
     *
     * <p>规则：
     * <ul>
     *   <li>tenantCode 留空 → 落入系统默认租户；非空 → 按 code 精确解析，找不到抛 TENANT_NOT_FOUND。</li>
     *   <li>同租户 username 必须唯一，否则抛 USERNAME_EXISTS。</li>
     *   <li>密码 BCrypt 加密落库，明文不再保留。</li>
     *   <li>新用户默认分配 STAFF 角色（普通员工，仅可访问公开知识库 / 创建私有 KB）。</li>
     *   <li>注册成功后直接颁发 JWT，与登录行为一致。</li>
     * </ul></p>
     */
    LoginResponse register(RegisterRequest req, HttpServletRequest httpReq);

    LoginResponse refresh(String refreshToken);

    /**
     * 内部 SSO 票据交换：用一次性 ticket 换取本系统 JWT，实现“一次登录、多处免登”。
     * ticket 无效/过期/已消费时抛 TOKEN_INVALID。
     */
    LoginResponse ssoExchange(String ticket, HttpServletRequest httpReq);

    /**
     * OIDC 登录桥接：把企业 IdP（Keycloak / Entra ID / Authing 等）认证通过的身份映射到本系统用户，
     * 并颁发本系统 JWT。仅在 'oidc' profile 激活时由 OIDC 登录成功处理器调用。
     *
     * <p>映射策略：按 username（IdP 的 preferred_username / sub）在指定租户内精确匹配已有用户；
     * 找不到或被禁用则抛业务异常。<b>不自动创建用户</b>，避免外部身份源把任意账号注入任意租户。</p>
     *
     * @param tenantCode IdP 身份归属的租户 code（可空，空则落默认租户）
     * @param username   IdP 提供的稳定用户名标识
     * @param httpReq    当前请求（取 IP 等）
     */
    LoginResponse oidcLogin(String tenantCode, String username, HttpServletRequest httpReq);

    void logout(String accessToken);
}
