package com.zwskchat.zwskchat.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切片器。
 */
public class TextSplitter {

    private static final double CHARS_PER_TOKEN = 3.5;
    private static final int DEFAULT_MAX_TOKENS = 400;
    private static final int MIN_CHUNK_CHARS = 80;

    /**
     * 纯文本切分（无页码信息）。
     */
    public static List<Chunk> split(String text, int maxTokens, int overlapTokens) {
        if (text == null || text.isBlank()) return List.of();

        int maxChars = normalizeMaxChars(maxTokens);
        int overlapChars = normalizeOverlapChars(overlapTokens, maxChars);
        List<Segment> segments = toSegments(text, null);
        return assembleChunks(segments, maxChars, overlapChars);
    }

    /**
     * 带页码的切分。
     *
     * @param pagedTexts    每页的文本内容
     * @param maxTokens     最大 token 数
     * @param overlapTokens 重叠 token 数
     * @return 切片列表，每个切片包含页码范围
     */
    public static List<Chunk> splitWithPages(List<PagedText> pagedTexts, int maxTokens, int overlapTokens) {
        if (pagedTexts == null || pagedTexts.isEmpty()) return List.of();

        int maxChars = normalizeMaxChars(maxTokens);
        int overlapChars = normalizeOverlapChars(overlapTokens, maxChars);
        List<Segment> segments = new ArrayList<>();
        for (PagedText pt : pagedTexts) {
            if (pt == null || pt.getContent() == null || pt.getContent().isBlank()) continue;
            segments.addAll(toSegments(pt.getContent(), pt.getPageNumber()));
        }
        return assembleChunks(segments, maxChars, overlapChars);
    }

    private static List<Segment> toSegments(String raw, Integer pageNumber) {
        String text = normalizeText(raw);
        if (text.isBlank()) return List.of();

        List<Segment> segments = new ArrayList<>();
        String[] parts = text.split("\\n\\s*\\n+");
        for (String part : parts) {
            String content = normalizeBlock(part);
            if (!content.isBlank()) {
                segments.add(new Segment(content, pageNumber, pageNumber));
            }
        }
        if (segments.isEmpty()) {
            segments.add(new Segment(text, pageNumber, pageNumber));
        }
        return segments;
    }

    private static List<Chunk> assembleChunks(List<Segment> segments, int maxChars, int overlapChars) {
        if (segments == null || segments.isEmpty()) return List.of();

        List<Chunk> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Integer startPage = null;
        Integer endPage = null;
        int idx = 0;

        for (Segment segment : segments) {
            if (segment == null || segment.content == null || segment.content.isBlank()) continue;
            String content = segment.content.trim();

            if (content.length() > maxChars) {
                if (buf.length() > 0) {
                    chunks.add(new Chunk(idx++, buf.toString().trim(), estimateTokens(buf.length()), startPage, endPage));
                    buf.setLength(0);
                    startPage = null;
                    endPage = null;
                }
                idx = appendLongSegment(chunks, idx, content, segment.startPage, segment.endPage, maxChars, overlapChars);
                continue;
            }

            int nextLen = buf.length() == 0 ? content.length() : buf.length() + 2 + content.length();
            if (buf.length() > 0 && nextLen > maxChars) {
                String chunkText = buf.toString().trim();
                chunks.add(new Chunk(idx++, chunkText, estimateTokens(chunkText.length()), startPage, endPage));

                String overlap = tailOverlap(chunkText, overlapChars);
                buf.setLength(0);
                if (!overlap.isBlank()) buf.append(overlap);
                startPage = segment.startPage;
                endPage = segment.endPage;
            }

            if (buf.length() > 0) buf.append("\n\n");
            buf.append(content);
            startPage = minPage(startPage, segment.startPage);
            endPage = maxPage(endPage, segment.endPage);
        }

        if (buf.length() > 0) {
            String chunkText = buf.toString().trim();
            chunks.add(new Chunk(idx, chunkText, estimateTokens(chunkText.length()), startPage, endPage));
        }
        return chunks;
    }

    private static int appendLongSegment(List<Chunk> chunks, int idx, String content,
                                         Integer startPage, Integer endPage,
                                         int maxChars, int overlapChars) {
        int from = 0;
        while (from < content.length()) {
            int end = findSafeEnd(content, from, maxChars);
            String piece = content.substring(from, end).trim();
            if (!piece.isBlank()) {
                chunks.add(new Chunk(idx++, piece, estimateTokens(piece.length()), startPage, endPage));
            }
            if (end >= content.length()) break;
            from = Math.max(from + 1, end - overlapChars);
            while (from < content.length() && Character.isWhitespace(content.charAt(from))) from++;
        }
        return idx;
    }

    private static int findSafeEnd(String text, int from, int maxChars) {
        int hardEnd = Math.min(text.length(), from + maxChars);
        if (hardEnd >= text.length()) return text.length();

        int min = from + Math.max(20, maxChars / 2);
        for (int i = hardEnd - 1; i >= min; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '.' || c == '!' || c == '?' || c == ';'
                    || Character.isWhitespace(c)) {
                return i + 1;
            }
        }
        return hardEnd;
    }

    private static String tailOverlap(String text, int overlapChars) {
        if (text == null || text.isBlank() || overlapChars <= 0) return "";
        if (text.length() <= overlapChars) return text;
        return text.substring(text.length() - overlapChars).trim();
    }

    private static String normalizeText(String raw) {
        if (raw == null) return "";
        String s = raw;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
        s = s.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00A0', ' ')
                .replace('\t', ' ');
        s = s.replaceAll("(?m)[ \\x0B\\f]+$", "");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    private static String normalizeBlock(String block) {
        if (block == null) return "";
        return normalizeText(block).trim();
    }

    private static int normalizeMaxChars(int maxTokens) {
        int safeTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        return (int) Math.max(MIN_CHUNK_CHARS, Math.round(safeTokens * CHARS_PER_TOKEN));
    }

    private static int normalizeOverlapChars(int overlapTokens, int maxChars) {
        int chars = (int) Math.max(0, Math.round(Math.max(0, overlapTokens) * CHARS_PER_TOKEN));
        return Math.min(chars, Math.max(0, maxChars / 2 - 1));
    }

    private static int estimateTokens(int len) {
        return (int) Math.ceil(len / CHARS_PER_TOKEN);
    }

    private static Integer minPage(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private static Integer maxPage(Integer a, Integer b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(a, b);
    }

    private record Segment(String content, Integer startPage, Integer endPage) {}

    /**
     * 带页码的文本输入。
     */
    @Data
    @AllArgsConstructor
    public static class PagedText {
        private int pageNumber;
        private String content;
    }

    /**
     * 切片结果（包含页码范围）。
     */
    @Data
    @AllArgsConstructor
    public static class Chunk {
        private int index;
        private String content;
        private int tokenCount;
        /** 起始页码（null 表示无页码信息） */
        private Integer startPage;
        /** 结束页码（null 表示无页码信息） */
        private Integer endPage;

        /**
         * 获取页码范围描述。
         */
        public String getPageRange() {
            if (startPage == null) return null;
            if (startPage.equals(endPage)) {
                return "第 " + startPage + " 页";
            }
            return "第 " + startPage + "-" + endPage + " 页";
        }
    }
}
