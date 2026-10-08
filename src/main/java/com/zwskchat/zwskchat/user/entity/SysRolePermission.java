package com.zwskchat.zwskchat.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_permission")
public class SysRolePermission {
    private Long id;
    private Long roleId;
    private Long permissionId;
    private LocalDateTime createTime;
}
