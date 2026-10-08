package com.zwskchat.zwskchat.voice.keyword.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordBatchUpsertRequest;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordResponse;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordUpsertRequest;
import com.zwskchat.zwskchat.voice.keyword.entity.VoiceKeyword;
import com.zwskchat.zwskchat.voice.keyword.mapper.VoiceKeywordMapper;
import com.zwskchat.zwskchat.voice.keyword.service.VoiceKeywordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceKeywordServiceImpl implements VoiceKeywordService {
    private static final Set<String> STOP_WORDS = Set.of(
            "这个", "那个", "然后", "就是", "一下", "我们", "你们", "他们", "它们", "什么", "怎么", "如何", "可以", "是不是",
            "帮我", "给我", "请问", "关于", "进行", "一个", "一些", "需要", "现在", "今天", "明天", "问题", "内容", "资料",
            "the", "and", "for", "with", "that", "this", "what", "how", "why", "can", "you", "your", "our", "please"
    );

    private final VoiceKeywordMapper keywordMapper;
    private final AiSessionMapper sessionMapper;
    private final AiChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<VoiceKeywordResponse> listMine(String q, Integer limit) {
        Long userId = SecurityUtils.currentUserId();
        int size = normalizeLimit(limit, 80, 200);
        String query = normalizeKeyword(q);
        List<VoiceKeyword> rows = keywordMapper.selectList(Wrappers.<VoiceKeyword>lambdaQuery()
                .eq(VoiceKeyword::getUserId, userId)
                .eq(VoiceKeyword::getStatus, 1)
                .like(query != null, VoiceKeyword::getKeyword, query)
                .orderByDesc(VoiceKeyword::getUpdateTime)
                .last("LIMIT " + size));
        return rows.stream().map(this::toResponse).toList();
    }

    @Override
    public List<String> extractKeywords(String text, Integer limit) {
        int size = normalizeLimit(limit, 8, 20);
        String source = text == null ? "" : text.trim();
        if (source.isBlank()) return List.of();

        String prompt = """
                你是企业语音对话的关键词抽取器。请从用户刚说的话中判断真正值得记录的关键词。
                要求：
                1. 只提取名词、专有名词、业务对象、产品/技术名、地点、人名、机构名、指标名、任务主题。
                2. 不要提取语气词、寒暄、泛词、代词、动词空话，例如：这个、那个、然后、帮我、需要、问题、内容、可以。
                3. 每个关键词 2 到 12 个中文字符或 2 到 32 个英文/数字字符，尽量短，不要整句。
                4. 最多返回 %d 个，按重要性排序，去重。
                5. 如果没有有价值关键词，返回 []。
                6. 必须只返回 JSON 字符串数组，例如 ["合同审核","知识库","Qdrant"]，不要解释，不要 Markdown。
                """.formatted(size);

        String user = "用户语音转写文本：\n" + source;
        String aiText;
        try {
            aiText = chatClient.streamChat(
                    List.of(ChatMessage.system(prompt), ChatMessage.user(user)),
                    null,
                    null);
        } catch (BusinessException e) {
            log.warn("[VoiceKeyword] AI keyword extraction failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("[VoiceKeyword] AI keyword extraction failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "AI 关键词判断失败");
        }

        List<String> parsed = parseAiKeywords(aiText, size);
        log.debug("[VoiceKeyword] AI extracted keywords text='{}' keywords={}", source, parsed);
        return parsed;
    }

    private List<String> parseAiKeywords(String aiText, int limit) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String json = extractJson(aiText);
        if (json == null) return List.of();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    addKeywordNode(result, node, limit);
                    if (result.size() >= limit) break;
                }
            } else if (root.isObject()) {
                JsonNode arr = root.path("keywords");
                if (arr.isArray()) {
                    for (JsonNode node : arr) {
                        addKeywordNode(result, node, limit);
                        if (result.size() >= limit) break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[VoiceKeyword] parse AI keyword json failed, raw={}", aiText, e);
        }
        return new ArrayList<>(result);
    }

    private void addKeywordNode(LinkedHashSet<String> result, JsonNode node, int limit) {
        if (result.size() >= limit || node == null || node.isNull()) return;
        String raw;
        if (node.isTextual()) raw = node.asText();
        else if (node.isObject()) raw = node.path("keyword").asText("");
        else raw = node.asText("");
        String keyword = normalizeKeyword(raw);
        if (keyword != null && !isStopWord(keyword)) result.add(keyword);
    }

    private String extractJson(String aiText) {
        if (aiText == null || aiText.isBlank()) return null;
        String s = aiText.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int arrStart = s.indexOf('[');
        int arrEnd = s.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) return s.substring(arrStart, arrEnd + 1);
        int objStart = s.indexOf('{');
        int objEnd = s.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) return s.substring(objStart, objEnd + 1);
        return null;
    }

    @Override
    @Transactional
    public VoiceKeywordResponse upsertManual(VoiceKeywordUpsertRequest req) {
        if (req == null) throw new BusinessException(ResultCode.BAD_REQUEST, "请求不能为空");
        String keyword = normalizeKeyword(req.getKeyword());
        if (keyword == null) throw new BusinessException(ResultCode.BAD_REQUEST, "关键词不能为空");
        return toResponse(upsertOne(keyword, req.getSourceText(), req.getSessionId(), req.getTurnId(), "MANUAL"));
    }

    @Override
    @Transactional
    public List<VoiceKeywordResponse> upsertBatch(VoiceKeywordBatchUpsertRequest req, String defaultSourceType) {
        if (req == null) return List.of();
        String sourceType = defaultSourceType == null || defaultSourceType.isBlank() ? "AUTO" : defaultSourceType.trim().toUpperCase(Locale.ROOT);
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        if (("AUTO".equals(sourceType) || "AI".equals(sourceType)) && req.getSourceText() != null && !req.getSourceText().isBlank()) {
            dedup.addAll(extractKeywords(req.getSourceText(), 8));
        } else if (req.getKeywords() != null) {
            for (String raw : req.getKeywords()) {
                String keyword = normalizeKeyword(raw);
                if (keyword != null && !isStopWord(keyword)) dedup.add(keyword);
                if (dedup.size() >= 20) break;
            }
        }
        List<VoiceKeywordResponse> saved = new ArrayList<>();
        for (String keyword : dedup) {
            saved.add(toResponse(upsertOne(keyword, req.getSourceText(), req.getSessionId(), req.getTurnId(), sourceType)));
        }
        return saved;
    }

    @Override
    @Transactional
    public void deleteMine(Long id) {
        if (id == null) return;
        VoiceKeyword row = keywordMapper.selectById(id);
        if (row == null) return;
        if (!row.getUserId().equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该关键词");
        }
        keywordMapper.deleteById(id);
    }

    private VoiceKeyword upsertOne(String keyword, String sourceText, Long sessionId, String turnId, String sourceType) {
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        if (sessionId != null) ensureOwnSession(sessionId, userId);
        VoiceKeyword existing = keywordMapper.selectOne(Wrappers.<VoiceKeyword>lambdaQuery()
                .eq(VoiceKeyword::getUserId, userId)
                .eq(VoiceKeyword::getKeyword, keyword)
                .last("LIMIT 1"));
        String snapshot = clip(sourceText, 1000);
        String safeTurnId = clip(turnId, 128);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            VoiceKeyword row = new VoiceKeyword();
            row.setTenantId(tenantId);
            row.setUserId(userId);
            row.setSessionId(sessionId);
            row.setTurnId(safeTurnId);
            row.setKeyword(keyword);
            row.setSourceType(sourceType);
            row.setSourceText(snapshot);
            row.setHitCount(1);
            row.setStatus(1);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            keywordMapper.insert(row);
            return row;
        }
        VoiceKeyword upd = new VoiceKeyword();
        upd.setId(existing.getId());
        upd.setSessionId(sessionId != null ? sessionId : existing.getSessionId());
        upd.setTurnId(safeTurnId != null ? safeTurnId : existing.getTurnId());
        upd.setSourceType(sourceType);
        if (snapshot != null) upd.setSourceText(snapshot);
        upd.setHitCount((existing.getHitCount() == null ? 0 : existing.getHitCount()) + 1);
        upd.setStatus(1);
        upd.setUpdateTime(now);
        keywordMapper.updateById(upd);
        VoiceKeyword merged = keywordMapper.selectById(existing.getId());
        return merged == null ? upd : merged;
    }

    private void ensureOwnSession(Long sessionId, Long userId) {
        AiSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权关联该语音会话");
        }
    }

    private VoiceKeywordResponse toResponse(VoiceKeyword row) {
        return VoiceKeywordResponse.builder()
                .id(row.getId())
                .tenantId(row.getTenantId())
                .userId(row.getUserId())
                .sessionId(row.getSessionId())
                .turnId(row.getTurnId())
                .keyword(row.getKeyword())
                .sourceType(row.getSourceType())
                .sourceText(row.getSourceText())
                .hitCount(row.getHitCount())
                .status(row.getStatus())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private int normalizeLimit(Integer limit, int defaultValue, int max) {
        if (limit == null || limit <= 0) return defaultValue;
        return Math.min(limit, max);
    }

    private String normalizeKeyword(String raw) {
        if (raw == null) return null;
        String s = raw.trim()
                .replaceAll("^[\\p{Punct}，。！？、；：‘’“”（）【】《》]+", "")
                .replaceAll("[\\p{Punct}，。！？、；：‘’“”（）【】《》]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (s.isBlank() || s.length() < 2) return null;
        return clip(s, 64);
    }

    private boolean isStopWord(String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT);
        return STOP_WORDS.contains(k) || STOP_WORDS.contains(keyword);
    }

    private String clip(String text, int max) {
        if (text == null) return null;
        String s = text.trim();
        if (s.isBlank()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
