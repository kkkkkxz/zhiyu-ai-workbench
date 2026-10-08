package com.zwskchat.zwskchat.rag.ingest;

import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentMapper;
import com.zwskchat.zwskchat.kb.parser.DocumentParser;
import com.zwskchat.zwskchat.rag.IndexingService;
import com.zwskchat.zwskchat.storage.ObjectStorage;
import com.zwskchat.zwskchat.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Consumes {@link IngestMessage} from {@code zwsk.ingest.queue} and runs the
 * synchronous indexing pipeline.
 *
 * <p>Failures bubble up so the configured {@code RetryTemplate} retries with
 * exponential back-off; once exhausted, the listener factory is configured with
 * {@code defaultRequeueRejected = false}, causing the broker to dead-letter
 * the message to {@code zwsk.ingest.dlx} -> {@code zwsk.ingest.dlq}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestListener {

    private final IndexingService indexingService;
    private final KnowledgeDocumentMapper documentMapper;
    private final ObjectStorage objectStorage;
    private final DocumentParser documentParser;

    @RabbitListener(queues = "${zwsk.ingest.queue:zwsk.ingest.queue}")
    public void onMessage(IngestMessage msg) {
        if (msg.getTraceId() != null) {
            MDC.put("traceId", msg.getTraceId());
        }
        try {
            String mode = msg.getObjectKey() != null ? "object" : "inline";
            log.info("[Ingest] receive doc={} tenant={} mode={} key={} bytes={}",
                    msg.getDocId(), msg.getTenantId(), mode, msg.getObjectKey(),
                    msg.getContent() == null ? 0 : msg.getContent().length());

            // listener thread has no SecurityContext; scope tenant explicitly
            try (TenantContext.Scope tenantScope = TenantContext.withTenant(msg.getTenantId())) {
                KnowledgeDocument doc = documentMapper.selectById(msg.getDocId());
                if (doc == null) {
                    log.warn("[Ingest] doc={} not found, drop message", msg.getDocId());
                    return; // ack, nothing to retry
                }
                if ("SUCCESS".equals(doc.getParseStatus())) {
                    log.info("[Ingest] doc={} already SUCCESS, drop duplicate message", msg.getDocId());
                    return;
                }
                doc.setParseStatus("PARSING");
                doc.setParseError(null);
                documentMapper.updateById(doc);

                // PDF 按页切分，其他文档按纯文本
                if ("pdf".equalsIgnoreCase(doc.getFileType()) && msg.getObjectKey() != null) {
                    byte[] pdfBytes = resolvePdfBytes(msg);
                    indexingService.indexDocument(doc, null, pdfBytes);
                } else {
                    String text = resolveText(msg);
                    indexingService.indexDocument(doc, text, null);
                }
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 取出待索引的纯文本：
     * <ul>
     *   <li>Object 模式：从 {@link ObjectStorage} 拉流，交 {@link DocumentParser} 抽取</li>
     *   <li>Inline 模式：直接使用消息体中的 {@link IngestMessage#getContent()}</li>
     * </ul>
     */
    private String resolveText(IngestMessage msg) {
        if (msg.getObjectKey() == null || msg.getObjectKey().isBlank()) {
            return msg.getContent();
        }
        try (InputStream in = objectStorage.openStream(msg.getObjectKey())) {
            return documentParser.parse(in, msg.getFileName());
        } catch (RuntimeException re) {
            // 让 BusinessException / ParseException 直接冒泡，由 retry / DLX 处理
            throw re;
        } catch (Exception e) {
            throw new IllegalStateException("读取对象存储失败 key=" + msg.getObjectKey(), e);
        }
    }

    /**
     * 取出 PDF 原始字节（用于按页切分）
     */
    private byte[] resolvePdfBytes(IngestMessage msg) {
        try (InputStream in = objectStorage.openStream(msg.getObjectKey())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("读取 PDF 字节失败 key=" + msg.getObjectKey(), e);
        }
    }
}
