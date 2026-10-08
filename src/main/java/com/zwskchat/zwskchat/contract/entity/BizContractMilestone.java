package com.zwskchat.zwskchat.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_contract_milestone")
public class BizContractMilestone {
    private Long id;
    private Long tenantId;
    private Long contractId;
    private String name;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime createTime;
}
