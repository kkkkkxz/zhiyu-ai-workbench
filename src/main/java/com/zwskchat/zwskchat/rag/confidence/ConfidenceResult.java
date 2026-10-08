package com.zwskchat.zwskchat.rag.confidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可信度评分结果。除最终分值与决策外，保留各分量明细，便于回溯与可解释性
 * （对应工程计划书“回溯机制”要求：每次回答可还原置信度构成）。
 *
 * @param confidence 组合后的总置信度，区间 [0,1]
 * @param decision   三档决策
 * @param components 各分量原始值（retrieval_coverage / rerank_margin / ...），区间 [0,1]
 */
public record ConfidenceResult(double confidence,
                               ConfidenceDecision decision,
                               Map<String, Double> components) {

    public ConfidenceResult {
        components = components == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    public boolean shouldAnswer() {
        return decision == ConfidenceDecision.ANSWER;
    }

    public boolean shouldRefuse() {
        return decision == ConfidenceDecision.REFUSE;
    }
}
