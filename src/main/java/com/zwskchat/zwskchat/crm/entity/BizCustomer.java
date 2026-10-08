package com.zwskchat.zwskchat.crm.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_customer")
public class BizCustomer {
    private Long id;
    private Long tenantId;
    private String name;
    private String shortName;
    private String type;
    private String industry;
    private String scale;
    private String source;
    private Long ownerId;
    private String status;
    private String grade;
    private Integer aiScore;
    private String lifecycleStage;
    private String aiNextAction;
    private java.math.BigDecimal aiValuePredict;
    private LocalDateTime aiScoredAt;
    private LocalDateTime lastFollowAt;
    private String phone;
    private String email;
    private String address;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
