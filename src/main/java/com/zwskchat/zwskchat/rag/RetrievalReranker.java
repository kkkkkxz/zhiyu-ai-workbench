package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 本地轻量重排器：在向量召回候选上叠加关键词覆盖度，提升短问句、专有名词和编号类问题的命中稳定性。
 */
@Component
public class RetrievalReranker {

    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "如何", "请问", "一下", "这个", "那个", "哪些", "是否", "可以",
            "的", "了", "吗", "呢", "请", "帮", "我", "你",
            "please", "what", "how", "the", "and", "for", "with", "that", "this", "can", "could");

    public List<RankedChunk> rerank(String question,
                                    List<Candidate> candidates,
                                    int limit,
                                    boolean enabled,
                                    double lexicalWeight) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) return List.of();

        Set<String> terms = enabled ? extractQueryTerms(question) : Set.of();
        String queryPhrase = enabled ? normalizeForMatch(question) : "";
        double safeLexicalWeight = enabled ? clamp(lexicalWeight, 0D, 0.8D) : 0D;
        double vectorWeight = 1D - safeLexicalWeight;

        List<RankedChunk> ranked = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.row() == null) continue;
            double vectorScore = candidate.vectorScore();
            double lexicalScore = enabled ? lexicalScore(question, queryPhrase, terms, candidate.row()) : 0D;
            double rankScore = clamp(vectorScore, 0D, 1D) * vectorWeight + lexicalScore * safeLexicalWeight;
            ranked.add(new RankedChunk(candidate.row(), vectorScore, lexicalScore, rankScore));
        }

        ranked.sort((a, b) -> {
            int byRank = Double.compare(b.rankScore(), a.rankScore());
            if (byRank != 0) return byRank;
            int byVector = Double.compare(b.vectorScore(), a.vectorScore());
            if (byVector != 0) return byVector;
            return Long.compare(nullLast(a.row().getId()), nullLast(b.row().getId()));
        });

        List<RankedChunk> result = new ArrayList<>(Math.min(limit, ranked.size()));
        Set<String> seenContent = new HashSet<>();
        for (RankedChunk item : ranked) {
            String fingerprint = fingerprint(item.row().getContent());
            if (!fingerprint.isBlank() && !seenContent.add(fingerprint)) continue;
            result.add(item);
            if (result.size() >= limit) break;
        }
        return result;
    }

    double lexicalScore(String question, KnowledgeChunk row) {
        Set<String> terms = extractQueryTerms(question);
        return lexicalScore(question, normalizeForMatch(question), terms, row);
    }

    private double lexicalScore(String question, String queryPhrase, Set<String> terms, KnowledgeChunk row) {
        if (row == null || terms == null || terms.isEmpty()) return 0D;

        String title = normalizeForMatch(row.getTitle());
        String content = normalizeForMatch(row.getContent());
        String combined = title + content;
        if (combined.isBlank()) return 0D;

        double total = 0D;
        double matched = 0D;
        double titleMatched = 0D;
        for (String term : terms) {
            double weight = termWeight(term);
            total += weight;
            if (combined.contains(term)) {
                matched += weight;
            }
            if (!title.isBlank() && title.contains(term)) {
                titleMatched += weight;
            }
        }
        if (total <= 0D) return 0D;

        double coverage = matched / total;
        double titleCoverage = titleMatched / total;
        double phraseBoost = queryPhrase.length() >= 4 && combined.contains(queryPhrase) ? 1D : 0D;
        if (phraseBoost == 0D && question != null && question.length() >= 4) {
            String shortQuestion = normalizeForMatch(question.replaceAll("[?？!！。,.，；;：:]+$", ""));
            phraseBoost = shortQuestion.length() >= 4 && combined.contains(shortQuestion) ? 1D : 0D;
        }
        return clamp(coverage * 0.80D + titleCoverage * 0.12D + phraseBoost * 0.08D, 0D, 1D);
    }

    public static Set<String> extractQueryTerms(String question) {
        if (question == null || question.isBlank()) return Set.of();

        Set<String> terms = new LinkedHashSet<>();
        StringBuilder cjkRun = new StringBuilder();
        StringBuilder wordRun = new StringBuilder();

        for (int i = 0; i < question.length(); i++) {
            char c = normalizeChar(question.charAt(i));
            if (isCjk(c)) {
                flushWordRun(wordRun, terms);
                cjkRun.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjkRun(cjkRun, terms);
                wordRun.append(c);
            } else {
                flushCjkRun(cjkRun, terms);
                flushWordRun(wordRun, terms);
            }
        }
        flushCjkRun(cjkRun, terms);
        flushWordRun(wordRun, terms);
        terms.removeIf(STOP_WORDS::contains);
        return terms;
    }

    private static void flushCjkRun(StringBuilder run, Set<String> terms) {
        if (run.isEmpty()) return;
        String text = run.toString();
        run.setLength(0);

        for (String part : removeCjkStopWords(text).split("\\s+")) {
            addCjkTerms(part, terms);
        }
    }

    private static void addCjkTerms(String text, Set<String> terms) {
        if (text == null || text.isBlank()) return;
        if (text.length() == 1) {
            terms.add(text);
            return;
        }
        for (int n = 2; n <= Math.min(4, text.length()); n++) {
            for (int i = 0; i <= text.length() - n; i++) {
                terms.add(text.substring(i, i + n));
            }
        }
        if (text.length() <= 12) {
            terms.add(text);
        }
    }

    private static String removeCjkStopWords(String text) {
        String result = text;
        for (String stopWord : STOP_WORDS) {
            if (stopWord.length() > 1 && isAllCjk(stopWord)) {
                result = result.replace(stopWord, " ");
            }
        }
        return result;
    }

    private static void flushWordRun(StringBuilder run, Set<String> terms) {
        if (run.isEmpty()) return;
        String text = run.toString();
        run.setLength(0);
        if (text.length() >= 2) {
            terms.add(text);
        }
    }

    private static String normalizeForMatch(String raw) {
        if (raw == null || raw.isBlank()) return "";

        StringBuilder sb = new StringBuilder(raw.length());
        String lower = raw.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = normalizeChar(lower.charAt(i));
            if (isCjk(c) || Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String fingerprint(String raw) {
        String normalized = normalizeForMatch(raw);
        if (normalized.length() <= 800) return normalized;
        return normalized.substring(0, 800);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isAllCjk(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!isCjk(text.charAt(i))) return false;
        }
        return true;
    }

    private static char normalizeChar(char c) {
        if (c == '\u3000') return ' ';
        if (c >= '\uFF01' && c <= '\uFF5E') {
            c = (char) (c - 0xFEE0);
        }
        return Character.toLowerCase(c);
    }

    private static double termWeight(String term) {
        if (term == null) return 0D;
        int len = term.length();
        if (len >= 6) return 1.50D;
        if (len >= 4) return 1.25D;
        if (len >= 3) return 1.10D;
        return 1D;
    }

    private static long nullLast(Long value) {
        return value == null ? Long.MAX_VALUE : value;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    public record Candidate(KnowledgeChunk row, double vectorScore) {
    }

    public record RankedChunk(KnowledgeChunk row, double vectorScore, double lexicalScore, double rankScore) {
    }
}
