package com.zwskchat.zwskchat.workorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.number.service.NumberRuleService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrderComment;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderCommentMapper;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderMapper;
import com.zwskchat.zwskchat.workorder.service.WorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final String DEFAULT_STATUS = "open";
    private static final String ASSIGNED_STATUS = "assigned";
    private static final String RESOLVED_STATUS = "resolved";
    private static final String CLOSED_STATUS = "closed";

    private final BizWorkOrderMapper orderMapper;
    private final BizWorkOrderCommentMapper commentMapper;
    private final NumberRuleService numberRuleService;

    @Override
    public IPage<BizWorkOrder> list(Page<BizWorkOrder> page, String status, String priority, String keyword) {
        LambdaQueryWrapper<BizWorkOrder> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(BizWorkOrder::getStatus, status);
        }
        if (priority != null && !priority.isBlank()) {
            query.eq(BizWorkOrder::getPriority, priority);
        }
        if (keyword != null && !keyword.isBlank()) {
            query.and(wrapper -> wrapper.like(BizWorkOrder::getTitle, keyword)
                    .or().like(BizWorkOrder::getOrderNo, keyword));
        }
        query.orderByDesc(BizWorkOrder::getCreateTime);
        return orderMapper.selectPage(page, query);
    }

    @Override
    public BizWorkOrder getById(Long id) {
        BizWorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "work order not found");
        }
        return order;
    }

    @Override
    @Transactional
    public BizWorkOrder create(BizWorkOrder order) {
        LoginUser user = SecurityUtils.currentUser();
        order.setTenantId(user.getTenantId());
        order.setOrderNo(numberRuleService.nextNumber("work_order", "WO"));
        order.setReporterId(user.getUserId());
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            order.setStatus(DEFAULT_STATUS);
        }
        orderMapper.insert(order);
        return order;
    }

    @Override
    @Transactional
    public BizWorkOrder update(Long id, BizWorkOrder order) {
        BizWorkOrder existing = getById(id);
        order.setId(id);
        order.setTenantId(existing.getTenantId());
        order.setOrderNo(existing.getOrderNo());
        order.setReporterId(existing.getReporterId());
        if (order.getStatus() == null || order.getStatus().isBlank()) {
            order.setStatus(existing.getStatus());
        }
        orderMapper.updateById(order);
        return orderMapper.selectById(id);
    }

    @Override
    @Transactional
    public void assign(Long id, Long assigneeId) {
        BizWorkOrder order = getById(id);
        order.setAssigneeId(assigneeId);
        order.setStatus(ASSIGNED_STATUS);
        orderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String status) {
        BizWorkOrder order = getById(id);
        order.setStatus(status);
        if (RESOLVED_STATUS.equals(status)) {
            order.setResolvedAt(LocalDateTime.now());
        }
        if (CLOSED_STATUS.equals(status)) {
            order.setClosedAt(LocalDateTime.now());
        }
        orderMapper.updateById(order);
    }

    @Override
    public List<BizWorkOrderComment> listComments(Long orderId) {
        getById(orderId);
        return commentMapper.selectList(new LambdaQueryWrapper<BizWorkOrderComment>()
                .eq(BizWorkOrderComment::getOrderId, orderId)
                .orderByAsc(BizWorkOrderComment::getCreateTime));
    }

    @Override
    @Transactional
    public BizWorkOrderComment addComment(Long orderId, BizWorkOrderComment comment) {
        getById(orderId);
        comment.setOrderId(orderId);
        comment.setUserId(SecurityUtils.currentUser().getUserId());
        commentMapper.insert(comment);
        return comment;
    }
}
