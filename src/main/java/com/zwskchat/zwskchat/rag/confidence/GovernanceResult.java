package com.zwskchat.zwskchat.rag.confidence;

import java.util.Collections;
import java.util.List;

/**
 * 回答治理结果（对应工程计划书“低置信拒答、输出带引导动作”原则）。
 *
 * @param action       治理动作
 * @param note         谨慎档追加的提示语；非 CAUTIOUS 时为空串
 * @param refusalText  拒答档的引导正文；仅在硬拒答时有意义，否则为空串
 * @param requiredInfo 建议补充的信息项（拒答档使用）
 * @param ctaActions   CTA 引导动作（答案 + 引用 + CTA）
 */
public record GovernanceResult(GovernanceAction action,
                               String note,
                               String refusalText,
                               List<String> requiredInfo,
                               List<String> ctaActions) {

    public GovernanceResult {
        note = note == null ? "" : note;
        refusalText = refusalText == null ? "" : refusalText;
        requiredInfo = requiredInfo == null ? List.of() : Collections.unmodifiableList(List.copyOf(requiredInfo));
        ctaActions = ctaActions == null ? List.of() : Collections.unmodifiableList(List.copyOf(ctaActions));
    }

    /** 是否应当用拒答引导话术替换正常回答。 */
    public boolean isHardRefusal() {
        return action == GovernanceAction.REFUSE_AND_GUIDE;
    }

    public enum GovernanceAction {
        /** 正常回答。 */
        ANSWER_NORMAL,
        /** 正常回答，但追加谨慎提示。 */
        ANSWER_WITH_CAUTION,
        /** 用补充信息 + 转人工/预约引导替换回答。 */
        REFUSE_AND_GUIDE
    }
}
