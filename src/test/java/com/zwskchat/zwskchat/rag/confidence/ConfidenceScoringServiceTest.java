package com.zwskchat.zwskchat.rag.confidence;

import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfidenceScoringServiceTest {

    private final AiProperties aiProps = new AiProperties();
    private final ConfidenceScoringService service = new ConfidenceScoringService(aiProps);

    @Test
    void noEvidenceRefuses() {
        ConfidenceResult r = service.score("差旅报销流程是什么", List.of());

        assertEquals(0D, r.components().get(ConfidenceScoringService.C_RETRIEVAL_COVERAGE));
        assertEquals(0D, r.components().get(ConfidenceScoringService.C_RERANK_MARGIN));
        assertSame(ConfidenceDecision.REFUSE, r.decision());
        assertTrue(r.confidence() < aiProps.getConfidence().getCautiousThreshold());
    }

    @Test
    void strongConsistentEvidenceScoresHighAndIsNotRefused() {
        // 多条同源、高分、强关键词覆盖、top1 鹤立鸡群 -> 至少进入谨慎档，不应被拒答
        ConfidenceResult r = service.score("差旅报销流程是什么", strongEvidence());

        assertTrue(r.components().get(ConfidenceScoringService.C_RETRIEVAL_COVERAGE) > 0.5,
                "关键词覆盖应较高");
        assertTrue(r.decision() != ConfidenceDecision.REFUSE,
                "强证据不应被拒答，实际=" + r.decision() + " conf=" + r.confidence());
    }

    @Test
    void strongEvidenceScoresHigherThanWeakEvidence() {
        ConfidenceResult strong = service.score("差旅报销流程是什么", strongEvidence());
        ConfidenceResult weak = service.score("差旅报销发票抵扣比例是多少", weakEvidence());

        assertTrue(strong.confidence() > weak.confidence(),
                "强证据置信度应高于弱证据：strong=" + strong.confidence() + " weak=" + weak.confidence());
    }

    @Test
    void weakCoverageIsNotAnswered() {
        // 命中但关键词覆盖弱、来源分散 -> 不应直接回答
        ConfidenceResult r = service.score("差旅报销发票抵扣比例是多少", weakEvidence());

        assertTrue(r.decision() != ConfidenceDecision.ANSWER,
                "弱覆盖证据不应直接回答，实际=" + r.decision() + " conf=" + r.confidence());
    }

    @Test
    void decisionRespectsConfiguredThresholds() {
        List<RetrievedChunk> chunks = strongEvidence();

        // 把阈值压到极低 -> 应判 ANSWER
        aiProps.getConfidence().setCautiousThreshold(0.05);
        aiProps.getConfidence().setAnswerThreshold(0.10);
        assertSame(ConfidenceDecision.ANSWER, service.score("差旅报销流程是什么", chunks).decision());

        // 把阈值抬到极高 -> 应判 REFUSE
        aiProps.getConfidence().setCautiousThreshold(0.99);
        aiProps.getConfidence().setAnswerThreshold(0.995);
        assertSame(ConfidenceDecision.REFUSE, service.score("差旅报销流程是什么", chunks).decision());
    }

    @Test
    void policyFailureDragsConfidenceDown() {
        List<RetrievedChunk> chunks = List.of(
                chunk(1L, 100L, "差旅报销制度", "差旅报销流程：提交发票、部门审批、财务复核、付款。", 0.95),
                chunk(2L, 100L, "差旅报销制度", "差旅报销流程还包含行程单与审批留痕。", 0.80));

        ConfidenceResult pass = service.score("差旅报销流程是什么", chunks, 1.0D);
        ConfidenceResult blocked = service.score("差旅报销流程是什么", chunks, 0.0D);

        assertTrue(blocked.confidence() < pass.confidence(),
                "策略不通过应拉低置信度");
        assertEquals(0D, blocked.components().get(ConfidenceScoringService.C_POLICY_PASS));
    }

    @Test
    void confidenceStaysWithinUnitInterval() {
        List<RetrievedChunk> chunks = List.of(
                chunk(1L, 100L, "差旅报销制度", "差旅报销流程：提交发票、部门审批、财务复核、付款。", 1.0));

        ConfidenceResult r = service.score("差旅报销流程是什么", chunks, 2.0D);

        assertTrue(r.confidence() >= 0D && r.confidence() <= 1D);
        // policy 入参越界应被裁剪到 [0,1]
        assertEquals(1D, r.components().get(ConfidenceScoringService.C_POLICY_PASS));
    }

    @Test
    void componentsAreImmutable() {
        ConfidenceResult r = service.score("任意问题", List.of());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> r.components().put("x", 1D));
    }

    @Test
    void authoritySignalUsesChunkMetadataWhenPresent() {
        // 高权威等级 5 应高于默认值 0.75；低权威等级 1 应低于默认值
        RetrievedChunk high = chunk(1L, 100L, "权威文档", "差旅报销流程说明。", 0.9);
        high.setAuthorityLevel(5);
        RetrievedChunk low = chunk(2L, 200L, "低权威文档", "差旅报销流程说明。", 0.9);
        low.setAuthorityLevel(1);

        double highAuthority = service.sourceAuthority(List.of(high), aiProps.getConfidence());
        double lowAuthority = service.sourceAuthority(List.of(low), aiProps.getConfidence());

        assertTrue(highAuthority > aiProps.getConfidence().getDefaultSourceAuthority());
        assertTrue(lowAuthority < aiProps.getConfidence().getDefaultSourceAuthority());
        assertEquals(1D, highAuthority, 1e-9);
        assertEquals(0D, lowAuthority, 1e-9);
    }

    @Test
    void authorityFallsBackToDefaultWhenUnlabeled() {
        RetrievedChunk c = chunk(1L, 100L, "未标注文档", "差旅报销流程说明。", 0.9);
        double authority = service.sourceAuthority(List.of(c), aiProps.getConfidence());
        assertEquals(aiProps.getConfidence().getDefaultSourceAuthority(), authority, 1e-9);
    }

    @Test
    void freshnessDecaysWithAge() {
        RetrievedChunk fresh = chunk(1L, 100L, "新文档", "内容。", 0.9);
        fresh.setFreshnessDate(java.time.LocalDateTime.now().minusDays(1));
        RetrievedChunk old = chunk(2L, 200L, "旧文档", "内容。", 0.9);
        old.setFreshnessDate(java.time.LocalDateTime.now()
                .minusDays((long) aiProps.getConfidence().getFreshnessHalfLifeDays() * 3));

        double freshScore = service.freshness(List.of(fresh), aiProps.getConfidence());
        double oldScore = service.freshness(List.of(old), aiProps.getConfidence());

        assertTrue(freshScore > 0.9, "近一天的内容时效分应接近 1，实际=" + freshScore);
        assertTrue(oldScore < freshScore, "更旧的内容时效分应更低");
    }

    @Test
    void freshnessHalfLifeIsApproximatelyHalf() {
        RetrievedChunk c = chunk(1L, 100L, "半衰期文档", "内容。", 0.9);
        c.setFreshnessDate(java.time.LocalDateTime.now()
                .minusDays((long) aiProps.getConfidence().getFreshnessHalfLifeDays()));
        double score = service.freshness(List.of(c), aiProps.getConfidence());
        assertEquals(0.5D, score, 0.02D);
    }

    @Test
    void freshnessFallsBackToDefaultWhenUnlabeled() {
        RetrievedChunk c = chunk(1L, 100L, "未标注", "内容。", 0.9);
        double score = service.freshness(List.of(c), aiProps.getConfidence());
        assertEquals(aiProps.getConfidence().getDefaultFreshness(), score, 1e-9);
    }

    private RetrievedChunk chunk(Long chunkId, Long documentId, String title, String content, double score) {
        return new RetrievedChunk(chunkId, documentId, 1L, title, content, score, null);
    }

    private List<RetrievedChunk> strongEvidence() {
        return List.of(
                chunk(1L, 100L, "差旅报销制度", "差旅报销流程：提交发票、部门审批、财务复核、付款。", 0.95),
                chunk(2L, 100L, "差旅报销制度", "差旅报销流程还包含行程单与审批留痕。", 0.78),
                chunk(3L, 100L, "差旅报销制度", "差旅报销流程的财务复核标准与时限说明。", 0.74));
    }

    private List<RetrievedChunk> weakEvidence() {
        return List.of(
                chunk(1L, 100L, "通用说明", "系统支持知识库与语音问答等通用能力。", 0.52),
                chunk(2L, 200L, "其它说明", "平台还提供普通对话与演示功能。", 0.50));
    }
}
