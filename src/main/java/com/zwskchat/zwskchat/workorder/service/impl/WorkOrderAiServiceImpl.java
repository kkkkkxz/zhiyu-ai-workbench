package com.zwskchat.zwskchat.workorder.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import com.zwskchat.zwskchat.user.entity.SysUser;
import com.zwskchat.zwskchat.user.mapper.SysUserMapper;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderMapper;
import com.zwskchat.zwskchat.workorder.service.WorkOrderAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderAiServiceImpl implements WorkOrderAiService {

    private final BizWorkOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final AiService aiService;
    private final TimelineService timelineService;

    private static final String SOLUTION_PROMPT = """
            你是售后服务工单专家。请根据当前工单和相似历史工单，给出分类和处理建议。
            当前工单:
            标题: %s
            类型: %s
            优先级: %s
            描述: %s
            
            相似历史工单:
            %s
            
            请输出100字内处理建议，包含可能原因、排查步骤、升级条件。
            """;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizWorkOrder analyzeAndSuggest(Long workOrderId) {
        BizWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            return null;
        }

        List<BizWorkOrder> similar = orderMapper.selectList(Wrappers.<BizWorkOrder>lambdaQuery()
                .ne(BizWorkOrder::getId, workOrderId)
                .and(w -> w.like(BizWorkOrder::getTitle, safeKeyword(order.getTitle()))
                        .or().eq(BizWorkOrder::getType, order.getType()))
                .orderByDesc(BizWorkOrder::getCreateTime)
                .last("LIMIT 5"));

        String similarText = similar.stream()
                .map(o -> o.getId() + ": " + o.getTitle() + " / " + o.getStatus() + " / " + nvl(o.getAiSuggestion()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format(SOLUTION_PROMPT,
                order.getTitle(), order.getType(), order.getPriority(), order.getDescription(), similarText);
        String suggestion = aiService.processTextWithAi(prompt, null).trim();

        order.setAiCategory(order.getType());
        order.setAiSuggestion(suggestion);
        order.setAiResolutionHint(suggestion);
        order.setAiSimilarIds(similar.stream().map(o -> String.valueOf(o.getId())).collect(Collectors.joining(",")));
        orderMapper.updateById(order);

        timelineService.record("work_order", workOrderId, "ai_action",
                "AI 生成工单处理建议", suggestion);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizWorkOrder smartAssign(Long workOrderId) {
        LoginUser loginUser = SecurityUtils.currentUser();
        BizWorkOrder order = orderMapper.selectById(workOrderId);
        if (order == null) {
            return null;
        }

        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, loginUser.getTenantId())
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId)
                .last("LIMIT 100"));

        SysUser selected = null;
        int minOpenOrders = Integer.MAX_VALUE;
        for (SysUser user : users) {
            Long count = orderMapper.selectCount(Wrappers.<BizWorkOrder>lambdaQuery()
                    .eq(BizWorkOrder::getAssigneeId, user.getId())
                    .in(BizWorkOrder::getStatus, List.of("assigned", "in_progress", "open")));
            if (count < minOpenOrders) {
                minOpenOrders = count.intValue();
                selected = user;
            }
        }

        if (selected == null) {
            return order;
        }

        order.setAssigneeId(selected.getId());
        order.setStatus("assigned");
        order.setAiAssignedReason("AI 根据当前待处理工单数选择负载最低人员: " + selected.getNickname() + "，当前未完成工单 " + minOpenOrders + " 个");
        if (order.getSlaDeadline() == null) {
            order.setSlaDeadline(LocalDateTime.now().plusHours(resolveSlaHours(order.getPriority())));
        }
        orderMapper.updateById(order);

        timelineService.record("work_order", workOrderId, "assigned",
                "AI 智能分配工单给 " + selected.getNickname(), order.getAiAssignedReason());
        return order;
    }

    @Override
    public Map<String, Object> progressReport() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizWorkOrder> orders = orderMapper.selectList(Wrappers.<BizWorkOrder>lambdaQuery()
                .eq(BizWorkOrder::getTenantId, user.getTenantId()));
        Map<String, Integer> statusCount = new HashMap<>();
        int overdue = 0;
        for (BizWorkOrder o : orders) {
            statusCount.merge(o.getStatus(), 1, Integer::sum);
            if (o.getSlaDeadline() != null && o.getSlaDeadline().isBefore(LocalDateTime.now())
                    && !List.of("resolved", "closed").contains(o.getStatus())) {
                overdue++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", orders.size());
        result.put("statusDistribution", statusCount);
        result.put("overdueCount", overdue);
        result.put("warning", overdue > 0 ? "存在 " + overdue + " 个超时工单，请优先处理" : "暂无超时工单");
        return result;
    }

    private String safeKeyword(String title) {
        if (title == null || title.isBlank()) return "";
        return title.length() <= 8 ? title : title.substring(0, 8);
    }

    private long resolveSlaHours(String priority) {
        return switch (priority == null ? "medium" : priority) {
            case "urgent" -> 4;
            case "high" -> 12;
            case "low" -> 72;
            default -> 24;
        };
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
