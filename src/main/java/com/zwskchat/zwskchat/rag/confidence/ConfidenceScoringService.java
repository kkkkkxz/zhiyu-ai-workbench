package com.zwskchat.zwskchat.rag.confidence;

import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.rag.RetrievalReranker;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 可信度组合评分服务。严格实现工程计划书给出的公式：
 * <pre>
 * confidence = 0.30*retrieval_coverage
 *            + 0.20*rerank_margin
 *            + 0.20*evidence_consistency
 *            + 0.15*source_authority
 *            + 0.10*freshness_score
 *            + 0.05*policy_pass_score
 * </pre>
 * 并据 answerThreshold / cautiousThreshold 给出三档决策。
 *
 * <p>各分量在当前数据模型下的计算口径：
 * <ul>
 *   <li><b>retrieval_coverage</b>：召回证据对查询关键词的覆盖度（命中词权重 / 总词权重）。
 *       无证据或无有效查询词时为 0。</li>
 *   <li><b>rerank_margin</b>：top1 与其余证据的相对分差，衡量最优证据是否“鹤立鸡群”。</li>
 *   <li><b>evidence_consistency</b>：证据来源集中度（主导文档占比）与分数紧致度的组合。</li>
 *   <li><b>source_authority</b>：来源权威度，元数据落地前取保守默认值。</li>
 *   <li><b>freshness_score</b>：时效性，元数据落地前取保守默认值。</li>
 *   <li><b>policy_pass_score</b>：安全 / 策略通过度，由上游传入（1.0 表示通过）。</li>
 * </ul>
 * 该服务无状态、不触达外部依赖，便于单元测试与回归。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfidenceScoringService {

    private final AiProperties aiProps;

    public static final String C_RETRIEVAL_COVERAGE = "retrieval_coverage";
    public static final String C_RERANK_MARGIN = "rerank_margin";
    public static final String C_EVIDENCE_CONSISTENCY = "evidence_consistency";
    public static final String C_SOURCE_AUTHORITY = "source_authority";
    public static final String C_FRESHNESS = "freshness_score";
    public static final String C_POLICY_PASS = "policy_pass_score";

    /**
     * 评分（默认策略通过）。
     */
    public ConfidenceResult score(String question, List<RetrievedChunk> chunks) {
        return score(question, chunks, 1.0D);
    }

    /**
     * 评分。
     *
     * @param question        用户问题
     * @param chunks          经检索 + 重排后的证据片段
     * @param policyPassScore 安全 / 策略通过度，区间 [0,1]，1.0 表示完全通过
     */
    public ConfidenceResult score(String question, List<RetrievedChunk> chunks, double policyPassScore) {
        AiProperties.Confidence cfg = aiProps.getConfidence();

        double coverage = retrievalCoverage(question, chunks);
        double margin = rerankMargin(chunks);
        double consistency = evidenceConsistency(chunks);
        double authority = sourceAuthority(chunks, cfg);
        double freshness = freshness(chunks, cfg);
        double policy = clamp01(policyPassScore);

        Map<String, Double> components = new LinkedHashMap<>();
        components.put(C_RETRIEVAL_COVERAGE, coverage);
        components.put(C_RERANK_MARGIN, margin);
        components.put(C_EVIDENCE_CONSISTENCY, consistency);
        components.put(C_SOURCE_AUTHORITY, authority);
        components.put(C_FRESHNESS, freshness);
        components.put(C_POLICY_PASS, policy);

        double confidence = clamp01(
                cfg.getWeightRetrievalCoverage() * coverage
                        + cfg.getWeightRerankMargin() * margin
                        + cfg.getWeightEvidenceConsistency() * consistency
                        + cfg.getWeightSourceAuthority() * authority
                        + cfg.getWeightFreshness() * freshness
                        + cfg.getWeightPolicyPass() * policy);

        ConfidenceDecision decision = decide(confidence, cfg);

        if (log.isDebugEnabled()) {
            log.debug("[Confidence] confidence={}, decision={}, components={}",
                    String.format(Locale.ROOT, "%.4f", confidence), decision, components);
        }
        return new ConfidenceResult(confidence, decision, components);
    }

    private ConfidenceDecision decide(double confidence, AiProperties.Confidence cfg) {
        if (confidence >= cfg.getAnswerThreshold()) return ConfidenceDecision.ANSWER;
        if (confidence >= cfg.getCautiousThreshold()) return ConfidenceDecision.CAUTIOUS;
        return ConfidenceDecision.REFUSE;
    }

    /**
     * 检索覆盖度：命中查询关键词的权重占比。中文短词权重低、长词/专名权重高，
     * 与 {@link RetrievalReranker} 的分词口径保持一致。
     */
    double retrievalCoverage(String question, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0D;
        Set<String> terms = RetrievalReranker.extractQueryTerms(question);
        if (terms.isEmpty()) return 0D;

        StringBuilder sb = new StringBuilder();
        for (RetrievedChunk c : chunks) {
            if (c == null) continue;
            if (c.getTitle() != null) sb.append(normalize(c.getTitle()));
            if (c.getContent() != null) sb.append(normalize(c.getContent()));
        }
        String evidence = sb.toString();
        if (evidence.isBlank()) return 0D;

        double total = 0D;
        double matched = 0D;
        for (String term : terms) {
            double w = termWeight(term);
            total += w;
            if (evidence.contains(term)) matched += w;
        }
        return total <= 0D ? 0D : clamp01(matched / total);
    }

    /**
     * 重排区分度：top1 与“其余证据平均分”的相对差。
     * 仅 1 条证据时给中性偏高值（无可比较项，但确实命中了）。
     */
    double rerankMargin(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0D;
        double top = chunks.get(0).getScore();
        for (RetrievedChunk c : chunks) {
            if (c != null) top = Math.max(top, c.getScore());
        }
        if (top <= 0D) return 0D;
        if (chunks.size() == 1) return 0.6D;

        double restSum = 0D;
        int restCount = 0;
        double best = top;
        boolean topSkipped = false;
        for (RetrievedChunk c : chunks) {
            if (c == null) continue;
            if (!topSkipped && c.getScore() == best) {
                topSkipped = true;
                continue;
            }
            restSum += Math.max(0D, c.getScore());
            restCount++;
        }
        if (restCount == 0) return 0.6D;
        double restAvg = restSum / restCount;
        return clamp01((top - restAvg) / top);
    }

    /**
     * 证据一致性：主导文档集中度（同一 documentId 占比）与分数紧致度的组合。
     * 来源越集中、分数越接近，越说明证据彼此印证。
     */
    double evidenceConsistency(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0D;
        if (chunks.size() == 1) return 0.6D;

        Map<Long, Integer> docCount = new LinkedHashMap<>();
        int counted = 0;
        double sum = 0D;
        double max = 0D;
        double min = Double.MAX_VALUE;
        for (RetrievedChunk c : chunks) {
            if (c == null) continue;
            Long docId = c.getDocumentId();
            docCount.merge(docId == null ? -1L : docId, 1, Integer::sum);
            double s = Math.max(0D, c.getScore());
            sum += s;
            max = Math.max(max, s);
            min = Math.min(min, s);
            counted++;
        }
        if (counted == 0) return 0D;

        int dominant = docCount.values().stream().max(Integer::compareTo).orElse(0);
        double concentration = (double) dominant / counted;

        double avg = sum / counted;
        // 分数紧致度：极差越小越紧致；用平均分归一化避免量纲问题。
        double tightness = avg <= 0D ? 0D : clamp01(1D - (max - min) / Math.max(avg, 1e-6));

        return clamp01(0.6D * concentration + 0.4D * tightness);
    }

    /**
     * 来源权威度：取命中证据中已标注 authority_level 的均值并归一化到 [0,1]；
     * 若所有证据均未标注，回退配置默认值。
     */
    double sourceAuthority(List<RetrievedChunk> chunks, AiProperties.Confidence cfg) {
        double fallback = clamp01(cfg.getDefaultSourceAuthority());
        if (chunks == null || chunks.isEmpty()) return fallback;

        int maxLevel = Math.max(1, cfg.getAuthorityMaxLevel());
        double sum = 0D;
        int counted = 0;
        for (RetrievedChunk c : chunks) {
            if (c == null || c.getAuthorityLevel() == null) continue;
            int level = Math.max(1, Math.min(maxLevel, c.getAuthorityLevel()));
            // 等级 1..maxLevel 归一化到 0..1（等级 1 -> 0，等级 maxLevel -> 1）
            sum += maxLevel == 1 ? 1D : (double) (level - 1) / (maxLevel - 1);
            counted++;
        }
        return counted == 0 ? fallback : clamp01(sum / counted);
    }

    /**
     * 时效性：对命中证据中已标注 freshness_date 的片段做半衰期衰减后取均值；
     * 未来日期按最新（1.0）处理；若所有证据均未标注，回退配置默认值。
     */
    double freshness(List<RetrievedChunk> chunks, AiProperties.Confidence cfg) {
        double fallback = clamp01(cfg.getDefaultFreshness());
        if (chunks == null || chunks.isEmpty()) return fallback;

        double halfLife = cfg.getFreshnessHalfLifeDays();
        if (halfLife <= 0D) return fallback;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        double sum = 0D;
        int counted = 0;
        for (RetrievedChunk c : chunks) {
            if (c == null || c.getFreshnessDate() == null) continue;
            double ageDays = java.time.Duration.between(c.getFreshnessDate(), now).toMinutes() / 1440.0D;
            if (ageDays < 0D) ageDays = 0D; // 未来日期视为最新
            // 半衰期衰减：score = 2^(-age/halfLife)，age=halfLife 时为 0.5
            sum += Math.pow(2D, -ageDays / halfLife);
            counted++;
        }
        return counted == 0 ? fallback : clamp01(sum / counted);
    }

    private static double termWeight(String term) {
        if (term == null) return 0D;
        int len = term.length();
        if (len >= 6) return 1.50D;
        if (len >= 4) return 1.25D;
        if (len >= 3) return 1.10D;
        return 1D;
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
