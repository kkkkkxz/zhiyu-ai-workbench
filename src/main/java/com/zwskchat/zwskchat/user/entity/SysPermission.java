package com.zwskchat.zwskchat.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class SysPermission {
    private Long id;
    private Long parentId;
    private String permCode;
    private String permName;
    /** menu / api / data */
    private String permType;
    private String path;
    private String method;
    private Integer sort;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
