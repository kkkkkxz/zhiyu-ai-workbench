package com.zwskchat.zwskchat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度 3-64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度 6-64")
    private String password;

    /**
     * 租户编码（可选）。
     * <ul>
     *   <li>单租户 / 系统中 username 全局唯一时：可不传，服务端按 username 直接定位用户；</li>
     *   <li>多租户且 username 在多个租户冲突时：服务端会返回"请提供租户编码"错误，
     *       前端再带上 tenantCode 重试登录。</li>
     * </ul>
     */
    @Size(max = 64, message = "租户编码长度 ≤ 64")
    private String tenantCode;

    private String captcha;
    private String captchaKey;
}
