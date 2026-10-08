package com.zwskchat.zwskchat.eval;

import com.zwskchat.zwskchat.rag.RetrievalReranker;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * RAG 评测器（RAGAS 思路的工程化实现，对应工程计划书“测试评估体系”章节）。
 *
 * <p>提供三组无需严格人工参考答案即可计算的指标：
 * <ul>
 *   <li>检索指标：hit@k、recall@k、precision@k、MRR；</li>
 *   <li>答案指标：faithfulness（答案是否被证据支撑）、keywordCoverage（覆盖应答关键词）、
 *       contextRelevance（证据与问题的相关度）；</li>
 *   <li>风险指标：风险问题是否被正确拒答。</li>
 * </ul>
 * 全部为纯函数，无外部依赖，便于单元测试与 CI 回归。
 */
@Component
public class RagEvaluator {

    /* ===================== 检索指标 ===================== */

    /** top-k 内是否命中任一期望文档。 */
    public boolean hitAtK(List<Long> expectedDocIds, List<Long> retrievedDocIds, int k) {
        if (isEmpty(expectedDocIds) || isEmpty(retrievedDocIds) || k <= 0) return false;
        Set<Long> expected = new LinkedHashSet<>(expectedDocIds);
        int limit = Math.min(k, retrievedDocIds.size());
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrievedDocIds.get(i))) return true;
        }
        return false;
    }

    /** top-k 内命中的期望文档占全部期望文档的比例。 */
    public double recallAtK(List<Long> expectedDocIds, List<Long> retrievedDocIds, int k) {
        if (isEmpty(expectedDocIds)) return 0D;
        if (isEmpty(retrievedDocIds) || k <= 0) return 0D;
        Set<Long> expected = new LinkedHashSet<>(expectedDocIds);
        Set<Long> topK = new LinkedHashSet<>(retrievedDocIds.subList(0, Math.min(k, retrievedDocIds.size())));
        long hit = expected.stream().filter(topK::contains).count();
        return clamp01((double) hit / expected.size());
    }

    /** top-k 内期望文档占召回结果的比例。 */
    public double precisionAtK(List<Long> expectedDocIds, List<Long> retrievedDocIds, int k) {
        if (isEmpty(expectedDocIds) || isEmpty(retrievedDocIds) || k <= 0) return 0D;
        Set<Long> expected = new LinkedHashSet<>(expectedDocIds);
        int limit = Math.min(k, retrievedDocIds.size());
        long hit = 0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrievedDocIds.get(i))) hit++;
        }
        return clamp01((double) hit / limit);
    }

    /** 平均倒数排名：第一个命中期望文档的位置倒数；未命中为 0。 */
    public double reciprocalRank(List<Long> expectedDocIds, List<Long> retrievedDocIds) {
        if (isEmpty(expectedDocIds) || isEmpty(retrievedDocIds)) return 0D;
        Set<Long> expected = new LinkedHashSet<>(expectedDocIds);
        for (int i = 0; i < retrievedDocIds.size(); i++) {
            if (expected.contains(retrievedDocIds.get(i))) {
                return 1D / (i + 1);
            }
        }
        return 0D;
    }

    /* ===================== 答案指标 ===================== */

    /**
     * faithfulness：答案中的关键词被证据支撑的比例（近似 RAGAS faithfulness）。
     * 答案词在证据中出现得越多，越说明答案"有据可依"、幻觉越少。
     */
    public double faithfulness(String answer, List<String> evidenceTexts) {
        Set<String> answerTerms = RetrievalReranker.extractQueryTerms(answer);
        if (answerTerms.isEmpty()) return 0D;
        String evidence = normalizeJoin(evidenceTexts);
        if (evidence.isBlank()) return 0D;
        long grounded = answerTerms.stream().filter(t -> evidence.contains(normalize(t))).count();
        return clamp01((double) grounded / answerTerms.size());
    }

    /** keywordCoverage：答案覆盖应答关键词的比例（近似 answer relevance）。 */
    public double keywordCoverage(String answer, List<String> expectedKeywords) {
        if (isEmpty(expectedKeywords)) return 0D;
        String normAnswer = normalize(answer);
        if (normAnswer.isBlank()) return 0D;
        long hit = expectedKeywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .filter(k -> normAnswer.contains(normalize(k)))
                .count();
        return clamp01((double) hit / expectedKeywords.size());
    }

    /** contextRelevance：证据对问题关键词的覆盖度（近似 RAGAS context relevance）。 */
    public double contextRelevance(String question, List<String> evidenceTexts) {
        Set<String> terms = RetrievalReranker.extractQueryTerms(question);
        if (terms.isEmpty()) return 0D;
        String evidence = normalizeJoin(evidenceTexts);
        if (evidence.isBlank()) return 0D;
        long hit = terms.stream().filter(t -> evidence.contains(normalize(t))).count();
        return clamp01((double) hit / terms.size());
    }

    /* ===================== 风险指标 ===================== */

    /** 风险问题是否被正确处理：应拒答则必须拒答，不应拒答则不能误拒。 */
    public boolean riskHandledCorrectly(boolean shouldRefuse, boolean actuallyRefused) {
        return shouldRefuse == actuallyRefused;
    }

    /* ===================== 工具 ===================== */

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static String normalizeJoin(List<String> texts) {
        if (isEmpty(texts)) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : texts) {
            if (t != null) sb.append(normalize(t));
        }
        return sb.toString();
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '\u3000') c = ' ';
            if (c >= '\uFF01' && c <= '\uFF5E') c = (char) (c - 0xFEE0);
            if (Character.isLetterOrDigit(c) || isCjk(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0D;
        return Math.max(0D, Math.min(1D, v));
    }
}
