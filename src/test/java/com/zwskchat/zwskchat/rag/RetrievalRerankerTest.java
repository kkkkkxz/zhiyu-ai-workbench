package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalRerankerTest {

    private final RetrievalReranker reranker = new RetrievalReranker();

    @Test
    void boostsExactBusinessTermsOverSlightlyHigherVectorScore() {
        KnowledgeChunk weakSemantic = chunk(1L, "通用说明", "系统支持知识库、语音问答和普通对话。");
        KnowledgeChunk exactPolicy = chunk(2L, "报销制度", "差旅报销流程包括提交发票、部门审批、财务复核和付款。");

        List<RetrievalReranker.RankedChunk> ranked = reranker.rerank(
                "差旅报销流程是什么",
                List.of(
                        new RetrievalReranker.Candidate(weakSemantic, 0.82),
                        new RetrievalReranker.Candidate(exactPolicy, 0.78)
                ),
                2,
                true,
                0.35);

        assertEquals(2L, ranked.get(0).row().getId());
        assertTrue(ranked.get(0).lexicalScore() > ranked.get(1).lexicalScore());
    }

    @Test
    void keepsVectorOrderWhenRerankDisabled() {
        KnowledgeChunk lower = chunk(1L, "报销制度", "差旅报销流程包括提交发票。");
        KnowledgeChunk higher = chunk(2L, "通用说明", "系统支持知识库问答。");

        List<RetrievalReranker.RankedChunk> ranked = reranker.rerank(
                "差旅报销流程是什么",
                List.of(
                        new RetrievalReranker.Candidate(lower, 0.70),
                        new RetrievalReranker.Candidate(higher, 0.90)
                ),
                2,
                false,
                0.35);

        assertEquals(2L, ranked.get(0).row().getId());
    }

    @Test
    void extractsChineseNgramsAndEnglishTerms() {
        Set<String> terms = RetrievalReranker.extractQueryTerms("请问 Qdrant 向量检索怎么配置？");

        assertTrue(terms.contains("qdrant"));
        assertTrue(terms.contains("向量"));
        assertTrue(terms.contains("检索"));
        assertTrue(terms.contains("配置"));
    }

    @Test
    void removesDuplicateContentFromFinalSelection() {
        KnowledgeChunk a = chunk(1L, "制度 A", "同一段报销制度内容，提交发票后进入审批。");
        KnowledgeChunk b = chunk(2L, "制度 B", "同一段报销制度内容，提交发票后进入审批。");
        KnowledgeChunk c = chunk(3L, "制度 C", "差旅报销还需要保留行程单。");

        List<RetrievalReranker.RankedChunk> ranked = reranker.rerank(
                "提交发票审批流程",
                List.of(
                        new RetrievalReranker.Candidate(a, 0.90),
                        new RetrievalReranker.Candidate(b, 0.89),
                        new RetrievalReranker.Candidate(c, 0.88)
                ),
                3,
                true,
                0.25);

        assertEquals(2, ranked.size());
        assertEquals(1L, ranked.get(0).row().getId());
        assertEquals(3L, ranked.get(1).row().getId());
    }

    private KnowledgeChunk chunk(Long id, String title, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setTitle(title);
        chunk.setContent(content);
        return chunk;
    }
}
