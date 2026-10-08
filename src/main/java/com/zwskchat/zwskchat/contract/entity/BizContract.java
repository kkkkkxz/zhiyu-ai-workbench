package com.zwskchat.zwskchat.contract.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_contract")
public class BizContract {
    private Long id;
    private Long tenantId;
    private String contractNo;
    private String title;
    private Long customerId;
    private String type;
    private BigDecimal amount;
    private String currency;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long ownerId;
    private String fileKey;
    private Integer aiRiskScore;
    private String aiRiskNote;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
