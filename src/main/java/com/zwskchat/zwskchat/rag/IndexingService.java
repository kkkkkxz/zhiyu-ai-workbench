package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.ai.AiEmbeddingClient;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeChunkMapper;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentMapper;
import com.zwskchat.zwskchat.kb.parser.PdfDocumentParser;
import com.zwskchat.zwskchat.vector.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 索引流水线：切片 → 向量化 → 写入 Qdrant + 关系库 chunk 表 + 更新文档状态。
 * M1 同步执行；M1.1 切到 RabbitMQ 异步任务以避免大文档阻塞。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final AiEmbeddingClient embeddingClient;
    private final QdrantClient qdrant;
    private final AiProperties aiProps;
    private final PdfDocumentParser pdfParser;
    private final PlatformTransactionManager txManager;

    @Transactional(rollbackFor = Exception.class)
    public void indexDocument(KnowledgeDocument doc, String fullText) {
        indexDocument(doc, fullText, null);
    }

    /**
     * 索引文档（支持 PDF 按页切分）
     *
     * @param doc 文档实体
     * @param fullText 全文（用于非 PDF 文档）
     * @param pdfBytes PDF 原始字节（用于 PDF 文档按页切分）
     */
    @Transactional(rollbackFor = Exception.class)
    public void indexDocument(KnowledgeDocument doc, String fullText, byte[] pdfBytes) {
        try {
            List<TextSplitter.Chunk> pieces;

            // 1. 切片：PDF 按页切分，其他文档按纯文本切分
            if (pdfBytes != null && "pdf".equalsIgnoreCase(doc.getFileType())) {
                pieces = indexPdfWithPages(doc, pdfBytes);
            } else {
                pieces = TextSplitter.split(fullText,
                        aiProps.getChunkMaxTokens(), aiProps.getChunkOverlapTokens());
            }

            if (pieces.isEmpty()) {
                doc.setParseStatus("FAILED");
                doc.setParseError("切片为空：文档无有效内容");
                doc.setChunkCount(0);
                documentMapper.updateById(doc);
                return;
            }

            // 2. 批量 embedding
            int batch = aiProps.getEmbedding().getBatchSize();
            List<float[]> allVectors = new ArrayList<>(pieces.size());
            for (int i = 0; i < pieces.size(); i += batch) {
                int end = Math.min(i + batch, pieces.size());
                List<String> texts = new ArrayList<>(end - i);
                for (int k = i; k < end; k++) texts.add(pieces.get(k).getContent());
                allVectors.addAll(embeddingClient.embed(texts));
            }
            if (allVectors.size() != pieces.size()) {
                throw new IllegalStateException("Embedding 数量不匹配：" + allVectors.size() + " vs " + pieces.size());
            }

            // 3. 落库 chunk 行（先拿 id 用作 vectorId）
            List<KnowledgeChunk> rows = new ArrayList<>(pieces.size());
            for (int i = 0; i < pieces.size(); i++) {
                TextSplitter.Chunk c = pieces.get(i);
                KnowledgeChunk row = new KnowledgeChunk();
                row.setTenantId(doc.getTenantId());
                row.setKbId(doc.getKbId());
                row.setDocumentId(doc.getId());
                row.setTitle(doc.getFileName());
                row.setContent(c.getContent());
                row.setChunkIndex(c.getIndex());
                row.setTokenCount(c.getTokenCount());
                // 冗余文档级治理元数据到 chunk，便于检索过滤与可信度评分（authority/freshness 真实信号）
                row.setAssetType(doc.getAssetType());
                row.setAuthorityLevel(doc.getAuthorityLevel());
                row.setConfidentialityLevel(doc.getConfidentialityLevel());
                row.setFreshnessDate(doc.getFreshnessDate());
                // 填充页码信息（如果有）
                if (c.getStartPage() != null) {
                    row.setPageNumber(c.getStartPage());
                }
                rows.add(row);
                chunkMapper.insert(row);
            }

            // 4. 写 Qdrant（payload 携带过滤字段）
            List<QdrantClient.UpsertPoint> points = new ArrayList<>(pieces.size());
            for (int i = 0; i < rows.size(); i++) {
                KnowledgeChunk row = rows.get(i);
                Map<String, Object> payload = new HashMap<>();
                payload.put("kb_id", row.getKbId());
                payload.put("document_id", row.getDocumentId());
                payload.put("chunk_index", row.getChunkIndex());
                payload.put("tenant_id", row.getTenantId());
                points.add(new QdrantClient.UpsertPoint(row.getId(), allVectors.get(i), payload));
                row.setVectorId(String.valueOf(row.getId()));
                chunkMapper.updateById(row);
            }
            qdrant.upsert(doc.getTenantId(), points);

            // 5. 更新文档状态
            doc.setChunkCount(rows.size());
            doc.setParseStatus("SUCCESS");
            doc.setParseError(null);
            documentMapper.updateById(doc);
            log.info("[Indexing] doc={} chunks={} indexed", doc.getId(), rows.size());

        } catch (Exception e) {
            log.error("[Indexing] doc={} failed", doc.getId(), e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            // 外层 @Transactional 会因为下面这句 throw 而回滚整个事务（包括 FAILED 状态的 update）；
            // 所以状态标记必须走独立事务。用 TransactionTemplate 程序式控制，避免 Spring AOP 同类
            // 调用 @Transactional 不生效的坑。仅记录状态，不报错起八丈，throw 仍交给上层。
            try {
                TransactionTemplate independent = new TransactionTemplate(txManager);
                independent.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
                independent.executeWithoutResult(status -> {
                    KnowledgeDocument failed = new KnowledgeDocument();
                    failed.setId(doc.getId());
                    failed.setParseStatus("FAILED");
                    failed.setParseError(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
                    documentMapper.updateById(failed);
                });
            } catch (Exception markEx) {
                log.warn("[Indexing] mark FAILED status independently failed doc={}: {}",
                        doc.getId(), markEx.getMessage());
            }
            throw new RuntimeException("索引失败：" + msg, e);
        }
    }

    /**
     * PDF 按页切分（保留页码信息）
     */
    private List<TextSplitter.Chunk> indexPdfWithPages(KnowledgeDocument doc, byte[] pdfBytes) {
        try {
            PdfDocumentParser.ParseResult result = pdfParser.parseWithPages(
                    new ByteArrayInputStream(pdfBytes), doc.getFileName());

            List<TextSplitter.PagedText> pagedTexts = result.getPages().stream()
                    .map(p -> new TextSplitter.PagedText(p.getPageNumber(), p.getContent()))
                    .collect(Collectors.toList());

            return TextSplitter.splitWithPages(pagedTexts,
                    aiProps.getChunkMaxTokens(), aiProps.getChunkOverlapTokens());
        } catch (Exception e) {
            log.error("[Indexing] PDF 按页解析失败，降级为纯文本切分", e);
            // 降级：使用 Tika 解析整个文档
            throw new RuntimeException("PDF 解析失败: " + e.getMessage(), e);
        }
    }
}
