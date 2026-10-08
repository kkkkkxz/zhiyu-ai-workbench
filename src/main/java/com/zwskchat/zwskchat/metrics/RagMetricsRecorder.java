package com.zwskchat.zwskchat.metrics;

import com.zwskchat.zwskchat.rag.confidence.ConfidenceDecision;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RAG 全链路可观测指标（对应工程计划书“监控栈 + 核心指标”）。
 *
 * <p>不依赖 OpenTelemetry Collector，直接复用已接入的 Micrometer/Prometheus 注册表，
 * 把检索命中、各阶段耗时、置信度决策分布、拒答率等核心指标暴露到 {@code /actuator/prometheus}：
 * <ul>
 *   <li>{@code zwsk_rag_retrieval}（Timer，检索耗时）+ {@code zwsk_rag_retrieval_hits}（命中片段数）</li>
 *   <li>{@code zwsk_rag_retrieval_miss_total}（零命中计数，监控 recall 漂移）</li>
 *   <li>{@code zwsk_rag_confidence_total{decision=...}}（三档决策分布）+ {@code zwsk_rag_confidence_score}</li>
 *   <li>{@code zwsk_rag_refusal_total}（拒答计数）</li>
 *   <li>{@code zwsk_rag_answer}（Timer，端到端问答耗时）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RagMetricsRecorder {

    private final MeterRegistry registry;

    /** 记录一次检索：命中片段数 + 耗时。 */
    public void recordRetrieval(int hitCount, long costMs) {
        registry.timer("zwsk.rag.retrieval").record(Duration.ofMillis(Math.max(0, costMs)));
        registry.summary("zwsk.rag.retrieval.hits").record(Math.max(0, hitCount));
        if (hitCount <= 0) {
            registry.counter("zwsk.rag.retrieval.miss").increment();
        }
    }

    /** 记录一次可信度决策：决策档位计数 + 分值分布。 */
    public void recordConfidence(ConfidenceDecision decision, double score) {
        String tag = decision == null ? "UNKNOWN" : decision.name();
        registry.counter("zwsk.rag.confidence", "decision", tag).increment();
        registry.summary("zwsk.rag.confidence.score").record(clamp01(score));
    }

    /** 记录一次硬拒答。 */
    public void recordRefusal() {
        registry.counter("zwsk.rag.refusal").increment();
    }

    /** 记录端到端问答耗时。 */
    public void recordAnswerLatency(long costMs) {
        registry.timer("zwsk.rag.answer").record(Duration.ofMillis(Math.max(0, costMs)));
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0D;
        return Math.max(0D, Math.min(1D, v));
    }
}
