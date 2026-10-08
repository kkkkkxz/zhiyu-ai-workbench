package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateTenantUserRequest {

    @Size(max = 64, message = "昵称不能超过64个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    @Size(max = 64, message = "手机号不能超过64个字符")
    private String phone;

    @Min(value = 0, message = "用户状态不合法")
    @Max(value = 2, message = "用户状态不合法")
    private Integer status;

    @Size(min = 8, max = 64, message = "密码长度必须为8-64位")
    private String password;

    private Set<String> roleCodes;
}
