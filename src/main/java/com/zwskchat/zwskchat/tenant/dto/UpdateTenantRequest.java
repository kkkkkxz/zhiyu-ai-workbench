package com.zwskchat.zwskchat.tenant.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateTenantRequest {

    @Size(max = 128, message = "租户名称不能超过128个字符")
    private String name;

    @Min(value = 0, message = "租户状态不合法")
    @Max(value = 1, message = "租户状态不合法")
    private Integer status;

    @Size(max = 64, message = "套餐编码不能超过64个字符")
    private String planCode;

    @Min(value = 1, message = "用户上限不能小于1")
    @Max(value = 100000, message = "用户上限不能超过100000")
    private Integer maxUsers;

    @Min(value = 1, message = "存储上限不能小于1MB")
    private Long maxStorageMb;

    private LocalDateTime expiresAt;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
