package com.zwskchat.zwskchat.workorder.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_work_order_comment")
public class BizWorkOrderComment {
    private Long id;
    private Long orderId;
    private Long userId;
    private String content;
    private Integer isInternal;
    private LocalDateTime createTime;
}
