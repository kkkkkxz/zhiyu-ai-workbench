package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateTenantUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度必须为3-32位")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{2,31}$", message = "用户名必须以字母开头，仅支持字母、数字、下划线、中划线和点")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度必须为8-64位")
    private String password;

    @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    @Size(max = 64, message = "手机号不能超过64个字符")
    private String phone;

    private Set<String> roleCodes;
}
