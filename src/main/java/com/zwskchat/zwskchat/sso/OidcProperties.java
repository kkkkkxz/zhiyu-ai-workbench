package com.zwskchat.zwskchat.sso;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OIDC 登录桥接配置（对应工程计划书“鉴权建议使用 OAuth 2.0 + OIDC 接入企业统一身份源”）。
 *
 * <p>仅在 'oidc' profile 激活时生效，与默认自管 JWT 链路并存、互不影响。
 * Spring Security 的 client 注册（issuer-uri / client-id 等）走标准
 * {@code spring.security.oauth2.client.*} 配置；本类只承载“把 IdP 身份映射到本系统用户”
 * 所需的少量业务参数。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "zwsk.oidc")
public class OidcProperties {

    /**
     * 从哪个 OIDC 用户属性取“本系统用户名”。默认 preferred_username；
     * 也可配 email / sub，取决于 IdP 与本系统账号的对齐方式。
     */
    private String usernameClaim = "preferred_username";

    /**
     * 从哪个 OIDC 用户属性取租户 code。留空表示所有 OIDC 用户落入 {@link #defaultTenantCode}。
     */
    private String tenantClaim = "";

    /** 当 tenantClaim 为空或取不到值时使用的租户 code（空=系统默认租户）。 */
    private String defaultTenantCode = "";

    /**
     * 登录成功后重定向到的前端地址，并以 query 形式回传本系统 token。
     * 例如 https://app.example.com/oidc/callback?token=xxx&refreshToken=yyy。
     */
    private String successRedirect = "/";

    /** 登录失败时重定向的前端地址。 */
    private String failureRedirect = "/login?oidc_error=1";
}
