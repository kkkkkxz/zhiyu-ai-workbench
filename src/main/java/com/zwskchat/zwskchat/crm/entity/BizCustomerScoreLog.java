package com.zwskchat.zwskchat.crm.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_customer_score_log")
public class BizCustomerScoreLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long customerId;

    private String oldGrade;

    private String newGrade;

    private Integer oldScore;

    private Integer newScore;

    private String oldStage;

    private String newStage;

    private String reason;

    private String nextAction;

    private String scoredBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
