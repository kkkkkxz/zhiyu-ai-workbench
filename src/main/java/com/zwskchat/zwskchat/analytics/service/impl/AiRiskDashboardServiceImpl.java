package com.zwskchat.zwskchat.analytics.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.analytics.service.AiRiskDashboardService;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizPaymentReminder;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.contract.mapper.BizPaymentReminderMapper;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.dispatch.entity.BizDispatch;
import com.zwskchat.zwskchat.dispatch.mapper.BizDispatchMapper;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.workorder.entity.BizWorkOrder;
import com.zwskchat.zwskchat.workorder.mapper.BizWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRiskDashboardServiceImpl implements AiRiskDashboardService {

    private final BizCustomerMapper customerMapper;
    private final BizContractMapper contractMapper;
    private final BizPaymentReminderMapper paymentReminderMapper;
    private final BizWorkOrderMapper workOrderMapper;
    private final BizDispatchMapper dispatchMapper;

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> result = new HashMap<>();
        result.put("customerRisk", customerRisk());
        result.put("contractRisk", contractRisk());
        result.put("workOrderRisk", workOrderRisk());
        result.put("dispatchRisk", dispatchRisk());
        return result;
    }

    @Override
    public Map<String, Object> customerRisk() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizCustomer> customers = customerMapper.selectList(Wrappers.<BizCustomer>lambdaQuery()
                .eq(BizCustomer::getTenantId, user.getTenantId()));
        int highValue = 0;
        int dormant = 0;
        int unscored = 0;
        for (BizCustomer c : customers) {
            if ("S".equals(c.getGrade()) || "A".equals(c.getGrade())) highValue++;
            if ("dormant".equals(c.getLifecycleStage()) || "lost".equals(c.getLifecycleStage())) dormant++;
            if (c.getAiScore() == null) unscored++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("total", customers.size());
        result.put("highValueCount", highValue);
        result.put("dormantOrLostCount", dormant);
        result.put("unscoredCount", unscored);
        result.put("warning", dormant > 0 ? "存在流失风险客户" : "客户风险正常");
        return result;
    }

    @Override
    public Map<String, Object> contractRisk() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizContract> contracts = contractMapper.selectList(Wrappers.<BizContract>lambdaQuery()
                .eq(BizContract::getTenantId, user.getTenantId()));
        List<BizPaymentReminder> reminders = paymentReminderMapper.selectList(Wrappers.<BizPaymentReminder>lambdaQuery()
                .eq(BizPaymentReminder::getTenantId, user.getTenantId())
                .ne(BizPaymentReminder::getStatus, "collected"));
        int highRiskContracts = 0;
        int endingSoon = 0;
        int overduePayments = 0;
        for (BizContract c : contracts) {
            if (c.getAiRiskScore() != null && c.getAiRiskScore() >= 70) highRiskContracts++;
            if (c.getEndDate() != null && !c.getEndDate().isBefore(LocalDate.now()) && c.getEndDate().isBefore(LocalDate.now().plusDays(15))) endingSoon++;
        }
        for (BizPaymentReminder r : reminders) {
            if (r.getDueDate() != null && r.getDueDate().isBefore(LocalDate.now())) overduePayments++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalContracts", contracts.size());
        result.put("highRiskContracts", highRiskContracts);
        result.put("endingSoonContracts", endingSoon);
        result.put("pendingPayments", reminders.size());
        result.put("overduePayments", overduePayments);
        result.put("warning", highRiskContracts > 0 || overduePayments > 0 ? "存在合同或回款风险" : "合同回款风险正常");
        return result;
    }

    @Override
    public Map<String, Object> workOrderRisk() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizWorkOrder> orders = workOrderMapper.selectList(Wrappers.<BizWorkOrder>lambdaQuery()
                .eq(BizWorkOrder::getTenantId, user.getTenantId()));
        int urgent = 0;
        int overdue = 0;
        int unassigned = 0;
        for (BizWorkOrder o : orders) {
            if ("urgent".equals(o.getPriority())) urgent++;
            if (o.getAssigneeId() == null && !"closed".equals(o.getStatus())) unassigned++;
            if (o.getSlaDeadline() != null && o.getSlaDeadline().isBefore(LocalDateTime.now())
                    && !List.of("resolved", "closed").contains(o.getStatus())) overdue++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalWorkOrders", orders.size());
        result.put("urgentCount", urgent);
        result.put("overdueCount", overdue);
        result.put("unassignedCount", unassigned);
        result.put("warning", overdue > 0 ? "存在超时工单" : unassigned > 0 ? "存在未指派工单" : "工单风险正常");
        return result;
    }

    @Override
    public Map<String, Object> dispatchRisk() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizDispatch> dispatches = dispatchMapper.selectList(Wrappers.<BizDispatch>lambdaQuery()
                .eq(BizDispatch::getTenantId, user.getTenantId()));
        int overdue = 0;
        int urgent = 0;
        for (BizDispatch d : dispatches) {
            if ("urgent".equals(d.getPriority())) urgent++;
            if (d.getDueDate() != null && d.getDueDate().isBefore(LocalDateTime.now())
                    && !"completed".equals(d.getStatus())) overdue++;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("totalDispatches", dispatches.size());
        result.put("urgentCount", urgent);
        result.put("overdueCount", overdue);
        result.put("warning", overdue > 0 ? "存在逾期派发任务" : "派发任务风险正常");
        return result;
    }
}
