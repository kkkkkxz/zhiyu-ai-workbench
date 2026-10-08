package com.zwskchat.zwskchat.workorder.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrderComment;

import java.util.List;

public interface WorkOrderService {
    IPage<BizWorkOrder> list(Page<BizWorkOrder> page, String status, String priority, String keyword);
    BizWorkOrder getById(Long id);
    BizWorkOrder create(BizWorkOrder order);
    BizWorkOrder update(Long id, BizWorkOrder order);
    void assign(Long id, Long assigneeId);
    void changeStatus(Long id, String status);

    List<BizWorkOrderComment> listComments(Long orderId);
    BizWorkOrderComment addComment(Long orderId, BizWorkOrderComment comment);
}
