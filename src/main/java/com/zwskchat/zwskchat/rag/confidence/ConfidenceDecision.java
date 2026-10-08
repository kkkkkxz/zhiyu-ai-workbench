package com.zwskchat.zwskchat.rag.confidence;

/**
 * 三档可信度决策（对应工程计划书“可信度评分”章节）。
 * <ul>
 *   <li>{@link #ANSWER} —— 置信度 ≥ answerThreshold，可直接据证据作答。</li>
 *   <li>{@link #CAUTIOUS} —— 置信度落在 [cautiousThreshold, answerThreshold)，应谨慎回答并明确不确定点。</li>
 *   <li>{@link #REFUSE} —— 置信度 &lt; cautiousThreshold，应拒答、补充信息或转人工 / 转专家。</li>
 * </ul>
 */
public enum ConfidenceDecision {
    ANSWER,
    CAUTIOUS,
    REFUSE
}
