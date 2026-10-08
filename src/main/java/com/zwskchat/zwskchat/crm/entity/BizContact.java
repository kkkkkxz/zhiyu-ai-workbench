package com.zwskchat.zwskchat.crm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_contact")
public class BizContact {
    private Long id;
    private Long tenantId;
    private Long customerId;
    private String name;
    private String position;
    private String phone;
    private String email;
    private Integer isPrimary;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
