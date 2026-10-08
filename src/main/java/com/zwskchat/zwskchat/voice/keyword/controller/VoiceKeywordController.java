package com.zwskchat.zwskchat.voice.keyword.controller;

import com.zwskchat.zwskchat.audit.annotation.AuditLog;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.ratelimit.annotation.RateLimit;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordBatchUpsertRequest;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordExtractRequest;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordResponse;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordUpsertRequest;
import com.zwskchat.zwskchat.voice.keyword.service.VoiceKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "语音关键词")
@RestController
@RequestMapping("/voice/keywords")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class VoiceKeywordController {
    private final VoiceKeywordService keywordService;

    @Operation(summary = "我的语音关键词列表")
    @GetMapping
    public Result<List<VoiceKeywordResponse>> list(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Integer limit) {
        return Result.success(keywordService.listMine(q, limit));
    }

    @Operation(summary = "从人类发言中提取关键词候选")
    @PostMapping("/extract")
    @RateLimit(key = "voice:keyword:extract", limit = 120, window = 60, dimension = RateLimit.Dimension.USER)
    public Result<List<String>> extract(@RequestBody @Valid VoiceKeywordExtractRequest req) {
        return Result.success(keywordService.extractKeywords(req.getText(), req.getLimit()));
    }

    @Operation(summary = "手动新增或更新关键词")
    @PostMapping
    @RateLimit(key = "voice:keyword:manual", limit = 60, window = 60, dimension = RateLimit.Dimension.USER)
    @AuditLog(operationType = "VOICE_KEYWORD_UPSERT", targetType = "voice_keyword")
    public Result<VoiceKeywordResponse> upsert(@RequestBody @Valid VoiceKeywordUpsertRequest req) {
        return Result.success(keywordService.upsertManual(req));
    }

    @Operation(summary = "批量保存自动提取的关键词")
    @PostMapping("/batch")
    @RateLimit(key = "voice:keyword:batch", limit = 120, window = 60, dimension = RateLimit.Dimension.USER)
    @AuditLog(operationType = "VOICE_KEYWORD_BATCH_UPSERT", targetType = "voice_keyword")
    public Result<List<VoiceKeywordResponse>> batch(@RequestBody @Valid VoiceKeywordBatchUpsertRequest req) {
        return Result.success(keywordService.upsertBatch(req, "AI"));
    }

    @Operation(summary = "删除我的关键词")
    @DeleteMapping("/{id}")
    @AuditLog(operationType = "VOICE_KEYWORD_DELETE", targetType = "voice_keyword", targetIdExpr = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        keywordService.deleteMine(id);
        return Result.success();
    }
}
