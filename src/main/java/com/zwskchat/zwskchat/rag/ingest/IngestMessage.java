package com.zwskchat.zwskchat.rag.ingest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Async document indexing message body.
 *
 * <p>支持两种取材路径，向后兼容：</p>
 * <ul>
 *   <li><b>Inline（M1.1）</b>：消息体直接带 {@link #content}（已抽取的纯文本）。
 *       适用于 .txt / .md 等小文档，listener 直接索引。</li>
 *   <li><b>Object（M2）</b>：消息只带 {@link #objectKey} + {@link #fileName}，
 *       listener 从对象存储拉流，交给 Tika 解析后再索引。
 *       推荐用于 PDF / DOCX / PPTX 等二进制文档，避免大消息体。</li>
 * </ul>
 *
 * <p>判定优先级：{@link #objectKey} 非空时走 Object 路径；否则走 Inline。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestMessage {
    /** Document id in {@code knowledge_document}. */
    private Long docId;
    /** Tenant id (carried explicitly so listener can scope without HTTP context). */
    private Long tenantId;
    /** Inline 模式下的纯文本内容；Object 模式下应为 null。 */
    private String content;
    /** Trace id for cross-component correlation. */
    private String traceId;
    /** Object 模式下的对象存储 key；Inline 模式下应为 null。 */
    private String objectKey;
    /** 原始文件名（含扩展名），辅助 Tika 格式探测；可与 KnowledgeDocument.fileName 不同。 */
    private String fileName;

    /** Inline 路径便捷构造器（保留兼容旧 4 参用法）。 */
    public static IngestMessage inline(Long docId, Long tenantId, String content, String traceId) {
        return new IngestMessage(docId, tenantId, content, traceId, null, null);
    }

    /** Object 路径便捷构造器。 */
    public static IngestMessage object(Long docId, Long tenantId, String objectKey, String fileName, String traceId) {
        return new IngestMessage(docId, tenantId, null, traceId, objectKey, fileName);
    }
}
