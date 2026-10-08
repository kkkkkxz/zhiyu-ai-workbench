package com.zwskchat.zwskchat.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <p>对应 {@code POST /auth/register}。注册成功后服务端直接颁发 JWT，与登录返回结构一致。</p>
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3-32")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_\\-.]{2,31}$",
            message = "用户名必须以英文字母开头，仅可包含字母 / 数字 / _ - .")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度 8-64")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "密码必须同时包含字母和数字")
    private String password;

    @Size(max = 64, message = "昵称长度 ≤ 64")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度 ≤ 128")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确（中国大陆）")
    private String phone;

    @NotNull(message = "请选择要加入的企业")
    private Long tenantId;

    /**
     * 租户编码（兼容旧客户端）。新注册页面必须传 tenantId。
     */
    @Size(max = 64, message = "租户编码长度 ≤ 64")
    private String tenantCode;
}
