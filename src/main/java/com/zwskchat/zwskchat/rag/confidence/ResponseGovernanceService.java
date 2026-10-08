package com.zwskchat.zwskchat.rag.confidence;

import com.zwskchat.zwskchat.config.properties.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回答治理服务：把可信度三档决策映射成具体的“回答 / 谨慎 / 拒答引导”动作，并附带 CTA。
 *
 * <p>映射规则：
 * <ul>
 *   <li>{@link ConfidenceDecision#ANSWER} → {@link GovernanceResult.GovernanceAction#ANSWER_NORMAL}</li>
 *   <li>{@link ConfidenceDecision#CAUTIOUS} → {@link GovernanceResult.GovernanceAction#ANSWER_WITH_CAUTION}（追加谨慎提示）</li>
 *   <li>{@link ConfidenceDecision#REFUSE} →
 *       开启硬拒答时为 {@link GovernanceResult.GovernanceAction#REFUSE_AND_GUIDE}；
 *       默认（不硬拒答）降级为 {@code ANSWER_WITH_CAUTION}，仅追加提示而不替换回答，避免回归既有体验。</li>
 * </ul>
 * CTA 在治理开启时始终给出，对应工程计划书“返回结果：答案 + 引用 + CTA”。
 * 该服务无状态、不触达外部依赖，便于单元测试。
 */
@Service
@RequiredArgsConstructor
public class ResponseGovernanceService {

    private final AiProperties aiProps;

    public GovernanceResult govern(ConfidenceResult confidence) {
        return govern(confidence, null);
    }

    /**
     * 在可信度决策基础上叠加“高风险槽位强制人工复核”规则。
     *
     * <p>当 {@code question} 命中报价 / 政策法律 / 实验参数 / 资质承诺 / 交付工期等高风险槽位，
     * 且高风险守卫开启时：即使可信度判为 ANSWER，也强制升级为“谨慎回答 + 人工复核提示”，
     * 直接防住越界承诺（对应工程计划书风险表“幻觉与越界承诺”缓解措施：槽位规则 + 人工复核）。
     * 硬拒答（REFUSE_AND_GUIDE）已是最强动作，不再叠加。</p>
     *
     * @param confidence 可信度评分结果
     * @param question   用户原始问题（用于高风险槽位检测；null 时跳过该规则）
     */
    public GovernanceResult govern(ConfidenceResult confidence, String question) {
        AiProperties.Governance cfg = aiProps.getGovernance();

        if (confidence == null || !cfg.isEnabled()) {
            return new GovernanceResult(GovernanceResult.GovernanceAction.ANSWER_NORMAL,
                    "", "", List.of(), List.of());
        }

        List<String> cta = cfg.getCtaSuggestions();

        GovernanceResult base = switch (confidence.decision()) {
            case ANSWER -> new GovernanceResult(
                    GovernanceResult.GovernanceAction.ANSWER_NORMAL,
                    "", "", List.of(), cta);
            case CAUTIOUS -> new GovernanceResult(
                    GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION,
                    cfg.getCautionNote(), "", List.of(), cta);
            case REFUSE -> cfg.isEnforceRefusal()
                    ? new GovernanceResult(
                            GovernanceResult.GovernanceAction.REFUSE_AND_GUIDE,
                            "", cfg.getRefusalMessage(), cfg.getRequiredInfo(), cta)
                    // 默认不硬拒答：降级为谨慎回答，避免替换正常回答造成体验回归。
                    : new GovernanceResult(
                            GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION,
                            cfg.getCautionNote(), "", List.of(), cta);
        };

        // 高风险槽位强制人工复核：命中即至少升级到“谨慎 + 人工复核提示”。
        if (cfg.isHighRiskSlotGuardEnabled() && HighRiskSlotDetector.hasHighRisk(question)
                && !base.isHardRefusal()) {
            String mergedNote = base.note() == null || base.note().isBlank()
                    ? cfg.getHighRiskNote()
                    : base.note() + " " + cfg.getHighRiskNote();
            return new GovernanceResult(
                    GovernanceResult.GovernanceAction.ANSWER_WITH_CAUTION,
                    mergedNote, "", List.of(), cta);
        }
        return base;
    }

    /**
     * 组装硬拒答的完整引导正文：开场 + 需补充信息清单。
     */
    public String renderRefusalText(GovernanceResult result) {
        if (result == null || !result.isHardRefusal()) return "";
        StringBuilder sb = new StringBuilder();
        if (!result.refusalText().isBlank()) {
            sb.append(result.refusalText());
        }
        if (!result.requiredInfo().isEmpty()) {
            sb.append("\n\n建议补充以下信息：");
            for (String item : result.requiredInfo()) {
                sb.append("\n- ").append(item);
            }
        }
        return sb.toString();
    }
}
