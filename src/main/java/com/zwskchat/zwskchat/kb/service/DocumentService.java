package com.zwskchat.zwskchat.kb.service;

import com.zwskchat.zwskchat.kb.dto.UploadTextRequest;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentGovernanceRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    /** 上传纯文本/Markdown 内容并索引（M1：inline 走消息体）。 */
    KnowledgeDocument uploadText(Long kbId, UploadTextRequest req);

    /**
     * 上传二进制文档（M2：PDF / DOCX / PPTX / XLSX / HTML / RTF / TXT / MD ...）。
     * <p>流程：扩展名白名单 → SHA-256 去重 → MinIO 持久化 → 发 ingest 消息 →
     * Listener 拉对象、Tika 解析、IndexingService 切片+向量化。</p>
     */
    KnowledgeDocument uploadFile(Long kbId, MultipartFile file);

    List<KnowledgeDocument> listByKb(Long kbId);

    /**
     * 录入/更新文档的知识治理元数据（资产类型、权威等级、时效、密级、可见范围等），
     * 并同步冗余到其全部 chunk，使可信度评分立即用上真实信号。仅更新非空字段。
     */
    KnowledgeDocument updateGovernance(Long docId, UpdateDocumentGovernanceRequest req);

    void delete(Long docId);
}
