package com.zwskchat.zwskchat.contract.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_payment_reminder")
public class BizPaymentReminder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long contractId;

    private Long milestoneId;

    private Long customerId;

    private String title;

    private BigDecimal amount;

    private LocalDate dueDate;

    private String priority;

    private String aiRiskLevel;

    private String aiSuggestion;

    private String status;

    private LocalDateTime notifiedAt;

    private LocalDateTime collectedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
