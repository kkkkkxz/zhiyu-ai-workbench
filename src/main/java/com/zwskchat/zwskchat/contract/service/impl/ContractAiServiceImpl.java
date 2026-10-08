package com.zwskchat.zwskchat.contract.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.contract.entity.BizContract;
import com.zwskchat.zwskchat.contract.entity.BizContractMilestone;
import com.zwskchat.zwskchat.contract.entity.BizPaymentReminder;
import com.zwskchat.zwskchat.contract.mapper.BizContractMapper;
import com.zwskchat.zwskchat.contract.mapper.BizContractMilestoneMapper;
import com.zwskchat.zwskchat.contract.mapper.BizPaymentReminderMapper;
import com.zwskchat.zwskchat.contract.service.ContractAiService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAiServiceImpl implements ContractAiService {

    private final BizContractMapper contractMapper;
    private final BizContractMilestoneMapper milestoneMapper;
    private final BizPaymentReminderMapper reminderMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final TimelineService timelineService;

    private static final String RISK_PROMPT = """
            你是合同风控助手。请分析以下合同的风险等级和风险说明。
            合同标题: %s
            合同编号: %s
            类型: %s
            金额: %s
            签署日期: %s
            起止日期: %s 至 %s
            当前状态: %s
            备注: %s
            回款节点: %s
            
            请严格返回JSON（不要markdown）:
            {"riskScore":0-100整数,"riskLevel":"LOW/MEDIUM/HIGH","riskNote":"风险说明100字内","suggestion":"审核建议100字内"}
            评分越高风险越高。重点关注付款条件不明、延期、金额过大、无回款节点、状态异常。
            """;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizContract analyzeRisk(Long contractId) {
        BizContract contract = contractMapper.selectById(contractId);
        if (contract == null) {
            return null;
        }
        List<BizContractMilestone> milestones = milestoneMapper.selectList(Wrappers.<BizContractMilestone>lambdaQuery()
                .eq(BizContractMilestone::getContractId, contractId)
                .orderByAsc(BizContractMilestone::getDueDate));

        String prompt = String.format(RISK_PROMPT,
                contract.getTitle(),
                contract.getContractNo(),
                contract.getType(),
                contract.getAmount(),
                contract.getSignDate(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getStatus(),
                contract.getRemark(),
                milestones.toString());

        String raw = aiService.processTextWithAi(prompt, null).trim();
        if (raw.startsWith("```")) raw = raw.replaceAll("```(?:json)?", "").trim();

        try {
            Map<String, Object> result = objectMapper.readValue(raw, new TypeReference<>() {});
            int riskScore = ((Number) result.getOrDefault("riskScore", 50)).intValue();
            String riskNote = String.valueOf(result.getOrDefault("riskNote", ""));
            String suggestion = String.valueOf(result.getOrDefault("suggestion", ""));

            contract.setAiRiskScore(riskScore);
            contract.setAiRiskNote(riskNote + (suggestion.isBlank() ? "" : "\n建议: " + suggestion));
            contractMapper.updateById(contract);

            timelineService.record("contract", contractId, "ai_action",
                    "AI 合同风险分析: " + riskScore + "分",
                    contract.getAiRiskNote());

            return contract;
        } catch (Exception e) {
            log.warn("[ContractAI] risk parse failed contract={} raw={}", contract.getContractNo(), raw, e);
            return contract;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BizPaymentReminder> generatePaymentReminders(int daysAhead) {
        LoginUser user = SecurityUtils.currentUser();
        LocalDate endDate = LocalDate.now().plusDays(Math.max(daysAhead, 1));
        List<BizContractMilestone> milestones = milestoneMapper.selectList(Wrappers.<BizContractMilestone>lambdaQuery()
                .le(BizContractMilestone::getDueDate, endDate)
                .ne(BizContractMilestone::getStatus, "completed")
                .orderByAsc(BizContractMilestone::getDueDate)
                .last("LIMIT 100"));

        List<BizPaymentReminder> created = new ArrayList<>();
        for (BizContractMilestone milestone : milestones) {
            BizContract contract = contractMapper.selectById(milestone.getContractId());
            if (contract == null || !Objects.equals(contract.getTenantId(), user.getTenantId())) {
                continue;
            }

            Long exists = reminderMapper.selectCount(Wrappers.<BizPaymentReminder>lambdaQuery()
                    .eq(BizPaymentReminder::getTenantId, user.getTenantId())
                    .eq(BizPaymentReminder::getMilestoneId, milestone.getId())
                    .ne(BizPaymentReminder::getStatus, "collected"));
            if (exists > 0) {
                continue;
            }

            BizPaymentReminder reminder = new BizPaymentReminder();
            reminder.setTenantId(user.getTenantId());
            reminder.setContractId(contract.getId());
            reminder.setMilestoneId(milestone.getId());
            reminder.setCustomerId(contract.getCustomerId());
            reminder.setTitle("回款提醒: " + contract.getTitle() + " - " + milestone.getName());
            reminder.setAmount(milestone.getAmount());
            reminder.setDueDate(milestone.getDueDate());
            reminder.setPriority(resolvePaymentPriority(contract, milestone));
            reminder.setAiRiskLevel(resolvePaymentRiskLevel(contract, milestone));
            reminder.setAiSuggestion(buildPaymentSuggestion(contract, milestone));
            reminder.setStatus("pending");
            reminderMapper.insert(reminder);
            created.add(reminder);

            timelineService.record("contract", contract.getId(), "ai_action",
                    "AI 生成回款提醒: " + milestone.getName(), reminder.getAiSuggestion());
        }
        return created;
    }

    @Override
    public List<BizPaymentReminder> pendingPaymentReminders() {
        LoginUser user = SecurityUtils.currentUser();
        return reminderMapper.selectList(Wrappers.<BizPaymentReminder>lambdaQuery()
                .eq(BizPaymentReminder::getTenantId, user.getTenantId())
                .in(BizPaymentReminder::getStatus, List.of("pending", "notified", "overdue"))
                .orderByAsc(BizPaymentReminder::getDueDate)
                .last("LIMIT 100"));
    }

    @Override
    public Map<String, Object> paymentRiskSummary() {
        List<BizPaymentReminder> reminders = pendingPaymentReminders();
        Map<String, Integer> riskCount = new HashMap<>();
        int overdue = 0;
        for (BizPaymentReminder r : reminders) {
            riskCount.merge(r.getAiRiskLevel() == null ? "UNKNOWN" : r.getAiRiskLevel(), 1, Integer::sum);
            if (r.getDueDate() != null && r.getDueDate().isBefore(LocalDate.now())) {
                overdue++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("pendingCount", reminders.size());
        result.put("overdueCount", overdue);
        result.put("riskDistribution", riskCount);
        result.put("items", reminders);
        return result;
    }

    private String resolvePaymentPriority(BizContract contract, BizContractMilestone milestone) {
        if (milestone.getDueDate() != null && !milestone.getDueDate().isAfter(LocalDate.now().plusDays(3))) {
            return "urgent";
        }
        if (contract.getAiRiskScore() != null && contract.getAiRiskScore() >= 70) {
            return "high";
        }
        return "medium";
    }

    private String resolvePaymentRiskLevel(BizContract contract, BizContractMilestone milestone) {
        if (milestone.getDueDate() != null && milestone.getDueDate().isBefore(LocalDate.now())) {
            return "HIGH";
        }
        if (contract.getAiRiskScore() != null && contract.getAiRiskScore() >= 70) {
            return "HIGH";
        }
        if (contract.getAiRiskScore() != null && contract.getAiRiskScore() >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildPaymentSuggestion(BizContract contract, BizContractMilestone milestone) {
        return "建议在 " + milestone.getDueDate() + " 前联系客户确认回款安排，优先核对合同编号 " + contract.getContractNo() + " 的付款条件。";
    }
}
