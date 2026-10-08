package com.zwskchat.zwskchat.rag.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.ai.AiEmbeddingClient;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeChunkMapper;
import com.zwskchat.zwskchat.vector.QdrantClient;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagReindexService {

    private final KnowledgeChunkMapper chunkMapper;
    private final AiEmbeddingClient embeddingClient;
    private final QdrantClient qdrant;
    private final AiProperties aiProperties;

    public ReindexResult reindexExistingChunks(Long tenantId, Long kbId, Integer limit, boolean dryRun) {
        LambdaQueryWrapper<KnowledgeChunk> q = Wrappers.lambdaQuery(KnowledgeChunk.class)
                .eq(KnowledgeChunk::getDeleted, 0)
                .orderByAsc(KnowledgeChunk::getTenantId, KnowledgeChunk::getKbId,
                        KnowledgeChunk::getDocumentId, KnowledgeChunk::getChunkIndex);
        if (tenantId != null) q.eq(KnowledgeChunk::getTenantId, tenantId);
        if (kbId != null) q.eq(KnowledgeChunk::getKbId, kbId);
        if (limit != null && limit > 0) q.last("LIMIT " + Math.min(limit, 10000));

        List<KnowledgeChunk> chunks = chunkMapper.selectList(q);
        if (dryRun) {
            return ReindexResult.builder()
                    .dryRun(true)
                    .matchedChunks(chunks.size())
                    .indexedChunks(0)
                    .batches(0)
                    .build();
        }

        int batchSize = Math.max(1, Math.min(aiProperties.getEmbedding().getBatchSize(), 10));
        int indexed = 0;
        int batches = 0;
        Map<Long, Integer> byTenant = new HashMap<>();

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<KnowledgeChunk> batch = chunks.subList(i, end);
            List<String> texts = batch.stream().map(KnowledgeChunk::getContent).toList();
            List<float[]> vectors = embeddingClient.embed(texts);
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException("Embedding count mismatch: " + vectors.size() + " vs " + batch.size());
            }

            Map<Long, List<QdrantClient.UpsertPoint>> pointsByTenant = new HashMap<>();
            for (int j = 0; j < batch.size(); j++) {
                KnowledgeChunk chunk = batch.get(j);
                Map<String, Object> payload = new HashMap<>();
                payload.put("kb_id", chunk.getKbId());
                payload.put("document_id", chunk.getDocumentId());
                payload.put("chunk_index", chunk.getChunkIndex());
                payload.put("tenant_id", chunk.getTenantId());
                pointsByTenant
                        .computeIfAbsent(chunk.getTenantId(), ignored -> new ArrayList<>())
                        .add(new QdrantClient.UpsertPoint(chunk.getId(), vectors.get(j), payload));
            }

            for (Map.Entry<Long, List<QdrantClient.UpsertPoint>> entry : pointsByTenant.entrySet()) {
                qdrant.upsert(entry.getKey(), entry.getValue());
                byTenant.merge(entry.getKey(), entry.getValue().size(), Integer::sum);
            }
            indexed += batch.size();
            batches += 1;
            log.info("[RAG-Reindex] batch indexed start={} size={}", i, batch.size());
        }

        return ReindexResult.builder()
                .dryRun(false)
                .matchedChunks(chunks.size())
                .indexedChunks(indexed)
                .batches(batches)
                .indexedByTenant(byTenant)
                .build();
    }

    @Data
    @Builder
    public static class ReindexResult {
        private boolean dryRun;
        private int matchedChunks;
        private int indexedChunks;
        private int batches;
        private Map<Long, Integer> indexedByTenant;
    }
}
