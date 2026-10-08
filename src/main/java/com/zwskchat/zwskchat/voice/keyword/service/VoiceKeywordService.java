package com.zwskchat.zwskchat.voice.keyword.service;

import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordBatchUpsertRequest;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordResponse;
import com.zwskchat.zwskchat.voice.keyword.dto.VoiceKeywordUpsertRequest;

import java.util.List;

public interface VoiceKeywordService {
    List<VoiceKeywordResponse> listMine(String q, Integer limit);

    List<String> extractKeywords(String text, Integer limit);

    VoiceKeywordResponse upsertManual(VoiceKeywordUpsertRequest req);

    List<VoiceKeywordResponse> upsertBatch(VoiceKeywordBatchUpsertRequest req, String defaultSourceType);

    void deleteMine(Long id);
}
