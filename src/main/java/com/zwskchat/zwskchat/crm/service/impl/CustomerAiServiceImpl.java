package com.zwskchat.zwskchat.crm.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.crm.entity.BizCustomer;
import com.zwskchat.zwskchat.crm.entity.BizCustomerScoreLog;
import com.zwskchat.zwskchat.crm.entity.BizFollowUp;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerMapper;
import com.zwskchat.zwskchat.crm.mapper.BizCustomerScoreLogMapper;
import com.zwskchat.zwskchat.crm.mapper.BizFollowUpMapper;
import com.zwskchat.zwskchat.crm.service.CustomerAiService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.timeline.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAiServiceImpl implements CustomerAiService {

    private final BizCustomerMapper customerMapper;
    private final BizCustomerScoreLogMapper scoreLogMapper;
    private final BizFollowUpMapper followUpMapper;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final TimelineService timelineService;

    private static final String SCORE_PROMPT = """
            你是企业 CRM 客户分析师。根据以下客户信息，给出评分和建议。
            
            客户信息:
            - 名称: %s
            - 类型: %s
            - 行业: %s
            - 当前状态: %s
            - 创建时间: %s
            - 最近跟进次数: %d
            - 最近跟进时间: %s
            
            请严格以JSON格式返回（不要包裹markdown）:
            {"score":0-100整数,"grade":"S/A/B/C/D","lifecycleStage":"new/potential/key/dormant/lost","nextAction":"具体建议动作","valuePredictYuan":预测年价值数字,"reason":"评分理由30字内"}
            
            评分规则:
            - S级(90-100): 高价值大客户，持续活跃
            - A级(75-89): 重要客户，有明确需求
            - B级(60-74): 潜力客户，需要培育
            - C级(40-59): 普通客户，跟进不积极
            - D级(0-39): 沉睡/流失风险客户
            """;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizCustomer scoreCustomer(Long customerId) {
        LoginUser user = SecurityUtils.currentUser();
        BizCustomer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            return null;
        }

        long followUpCount = followUpMapper.selectCount(Wrappers.<BizFollowUp>lambdaQuery()
                .eq(BizFollowUp::getCustomerId, customerId));

        BizFollowUp latestFollowUp = followUpMapper.selectOne(Wrappers.<BizFollowUp>lambdaQuery()
                .eq(BizFollowUp::getCustomerId, customerId)
                .orderByDesc(BizFollowUp::getCreateTime)
                .last("LIMIT 1"));

        String lastFollowTime = latestFollowUp != null ? latestFollowUp.getCreateTime().toString() : "无";

        String prompt = String.format(SCORE_PROMPT,
                customer.getName(),
                customer.getType(),
                customer.getIndustry(),
                customer.getStatus(),
                customer.getCreateTime() != null ? customer.getCreateTime().toString().substring(0, 10) : "未知",
                followUpCount,
                lastFollowTime);

        String aiResult = aiService.processTextWithAi(prompt, null).trim();
        if (aiResult.startsWith("```")) {
            aiResult = aiResult.replaceAll("```(?:json)?", "").trim();
        }

        try {
            Map<String, Object> result = objectMapper.readValue(aiResult, new TypeReference<>() {});

            String oldGrade = customer.getGrade();
            Integer oldScore = customer.getAiScore();
            String oldStage = customer.getLifecycleStage();

            int score = ((Number) result.getOrDefault("score", 50)).intValue();
            String grade = (String) result.getOrDefault("grade", "C");
            String stage = (String) result.getOrDefault("lifecycleStage", "potential");
            String nextAction = (String) result.getOrDefault("nextAction", "");
            Number valuePredictRaw = (Number) result.get("valuePredictYuan");
            String reason = (String) result.getOrDefault("reason", "");

            customer.setAiScore(score);
            customer.setGrade(grade);
            customer.setLifecycleStage(stage);
            customer.setAiNextAction(nextAction);
            if (valuePredictRaw != null) {
                customer.setAiValuePredict(new java.math.BigDecimal(valuePredictRaw.toString()));
            }
            customer.setAiScoredAt(LocalDateTime.now());
            if (latestFollowUp != null) {
                customer.setLastFollowAt(latestFollowUp.getCreateTime());
            }
            customerMapper.updateById(customer);

            BizCustomerScoreLog scoreLog = new BizCustomerScoreLog();
            scoreLog.setTenantId(user.getTenantId());
            scoreLog.setCustomerId(customerId);
            scoreLog.setOldGrade(oldGrade);
            scoreLog.setNewGrade(grade);
            scoreLog.setOldScore(oldScore);
            scoreLog.setNewScore(score);
            scoreLog.setOldStage(oldStage);
            scoreLog.setNewStage(stage);
            scoreLog.setReason(reason);
            scoreLog.setNextAction(nextAction);
            scoreLog.setScoredBy("ai");
            scoreLogMapper.insert(scoreLog);

            timelineService.record("customer", customerId, "ai_action",
                    "AI 评分: " + grade + "(" + score + "分)",
                    reason + " | 建议: " + nextAction);

            log.info("[CustomerAI] scored customer={} grade={} score={}", customer.getName(), grade, score);
            return customer;
        } catch (Exception e) {
            log.warn("[CustomerAI] score parse failed customer={} raw={}", customer.getName(), aiResult, e);
            return customer;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<BizCustomer> batchScore() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizCustomer> customers = customerMapper.selectList(Wrappers.<BizCustomer>lambdaQuery()
                .eq(BizCustomer::getTenantId, user.getTenantId())
                .and(w -> w.isNull(BizCustomer::getAiScoredAt)
                        .or().lt(BizCustomer::getAiScoredAt, LocalDateTime.now().minusDays(7)))
                .last("LIMIT 20"));

        List<BizCustomer> scored = new ArrayList<>();
        for (BizCustomer c : customers) {
            try {
                BizCustomer result = scoreCustomer(c.getId());
                if (result != null) scored.add(result);
            } catch (Exception e) {
                log.warn("[CustomerAI] batch score failed for customer={}", c.getName(), e);
            }
        }
        return scored;
    }

    @Override
    public List<BizCustomerScoreLog> scoreHistory(Long customerId) {
        return scoreLogMapper.selectList(Wrappers.<BizCustomerScoreLog>lambdaQuery()
                .eq(BizCustomerScoreLog::getCustomerId, customerId)
                .orderByDesc(BizCustomerScoreLog::getCreateTime)
                .last("LIMIT 50"));
    }

    @Override
    public Map<String, Object> lifecycleSummary() {
        LoginUser user = SecurityUtils.currentUser();
        List<BizCustomer> all = customerMapper.selectList(Wrappers.<BizCustomer>lambdaQuery()
                .eq(BizCustomer::getTenantId, user.getTenantId()));

        Map<String, Integer> stageCount = new HashMap<>();
        Map<String, Integer> gradeCount = new HashMap<>();
        int totalScore = 0;
        int scored = 0;

        for (BizCustomer c : all) {
            String stage = c.getLifecycleStage() != null ? c.getLifecycleStage() : "unscored";
            stageCount.merge(stage, 1, Integer::sum);

            String grade = c.getGrade() != null ? c.getGrade() : "unscored";
            gradeCount.merge(grade, 1, Integer::sum);

            if (c.getAiScore() != null) {
                totalScore += c.getAiScore();
                scored++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalCustomers", all.size());
        result.put("scoredCount", scored);
        result.put("avgScore", scored > 0 ? totalScore / scored : 0);
        result.put("stageDistribution", stageCount);
        result.put("gradeDistribution", gradeCount);
        return result;
    }
}
