package com.zwskchat.zwskchat.metrics;

import com.zwskchat.zwskchat.rag.confidence.ConfidenceDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagMetricsRecorderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RagMetricsRecorder recorder = new RagMetricsRecorder(registry);

    @Test
    void recordsRetrievalTimerAndHits() {
        recorder.recordRetrieval(3, 120);

        assertEquals(1, registry.timer("zwsk.rag.retrieval").count());
        assertEquals(1, registry.summary("zwsk.rag.retrieval.hits").count());
        assertEquals(3.0, registry.summary("zwsk.rag.retrieval.hits").totalAmount(), 1e-9);
    }

    @Test
    void zeroHitsIncrementsMissCounter() {
        recorder.recordRetrieval(0, 50);
        assertEquals(1.0, registry.counter("zwsk.rag.retrieval.miss").count(), 1e-9);
    }

    @Test
    void nonZeroHitsDoesNotIncrementMiss() {
        recorder.recordRetrieval(2, 50);
        assertEquals(0.0, registry.counter("zwsk.rag.retrieval.miss").count(), 1e-9);
    }

    @Test
    void confidenceDecisionTaggedAndScoreRecorded() {
        recorder.recordConfidence(ConfidenceDecision.CAUTIOUS, 0.72);
        recorder.recordConfidence(ConfidenceDecision.CAUTIOUS, 0.66);
        recorder.recordConfidence(ConfidenceDecision.REFUSE, 0.4);

        assertEquals(2.0, registry.counter("zwsk.rag.confidence", "decision", "CAUTIOUS").count(), 1e-9);
        assertEquals(1.0, registry.counter("zwsk.rag.confidence", "decision", "REFUSE").count(), 1e-9);
        assertEquals(3, registry.summary("zwsk.rag.confidence.score").count());
    }

    @Test
    void nullDecisionTaggedUnknown() {
        recorder.recordConfidence(null, 0.5);
        assertEquals(1.0, registry.counter("zwsk.rag.confidence", "decision", "UNKNOWN").count(), 1e-9);
    }

    @Test
    void refusalAndAnswerLatencyRecorded() {
        recorder.recordRefusal();
        recorder.recordRefusal();
        recorder.recordAnswerLatency(800);

        assertEquals(2.0, registry.counter("zwsk.rag.refusal").count(), 1e-9);
        assertEquals(1, registry.timer("zwsk.rag.answer").count());
    }
}
