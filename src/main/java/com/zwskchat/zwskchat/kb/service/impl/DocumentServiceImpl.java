package com.zwskchat.zwskchat.kb.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.IngestProperties;
import com.zwskchat.zwskchat.config.properties.MinioProperties;
import com.zwskchat.zwskchat.kb.dto.UploadTextRequest;
import com.zwskchat.zwskchat.kb.dto.UpdateDocumentGovernanceRequest;
import com.zwskchat.zwskchat.kb.entity.KnowledgeChunk;
import com.zwskchat.zwskchat.kb.entity.KnowledgeDocument;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeChunkMapper;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeDocumentMapper;
import com.zwskchat.zwskchat.kb.parser.DocumentParser;
import com.zwskchat.zwskchat.kb.service.DocumentService;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import com.zwskchat.zwskchat.rag.IndexingService;
import com.zwskchat.zwskchat.rag.ingest.IngestProducer;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.storage.ObjectKeyBuilder;
import com.zwskchat.zwskchat.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long DEFAULT_MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 上传白名单：扩展名（小写，含点）。Tika 仍会做嗅探兜底。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown",
            ".pdf",
            ".doc", ".docx",
            ".xls", ".xlsx", ".csv",
            ".ppt", ".pptx",
            ".html", ".htm",
            ".rtf"
    );

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeBaseService kbService;
    private final IndexingService indexingService;
    private final IngestProducer ingestProducer;
    private final IngestProperties ingestProps;
    private final ObjectStorage objectStorage;
    private final ObjectKeyBuilder objectKeyBuilder;
    private final MinioProperties minioProps;
    private final DocumentParser documentParser;

    @Override
    public KnowledgeDocument uploadText(Long kbId, UploadTextRequest req) {
        kbService.assertWritable(kbId);

        String name = req.getFileName().trim();
        String lower = name.toLowerCase();
        if (!(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown"))) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "M1 仅支持 .txt / .md");
        }
        String content = req.getContent();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);

        // 同租户同 KB 同 hash 视为重复
        KnowledgeDocument exists = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKbId, kbId)
                .eq(KnowledgeDocument::getFileHash, hash)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文档已存在（hash 相同），id=" + exists.getId());
        }

        boolean async = ingestProps.getAsync().isEnabled();
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(SecurityUtils.currentUser().getTenantId());
        doc.setKbId(kbId);
        doc.setFileName(name);
        doc.setFileType(lower.endsWith(".md") || lower.endsWith(".markdown") ? "md" : "txt");
        doc.setFileSize((long) bytes.length);
        doc.setFileHash(hash);
        // M1：暂存为 inline，文件不入 MinIO；M2 走对象存储
        doc.setFileUrl("inline://" + hash);
        doc.setParseStatus(async ? "PENDING" : "PARSING");
        doc.setUploaderId(SecurityUtils.currentUserId());
        doc.setChunkCount(0);
        documentMapper.insert(doc);

        if (async) {
            // M1.1: 异步索引；前端轮询 parseStatus
            try {
                ingestProducer.publish(doc.getId(), doc.getTenantId(), content);
            } catch (Exception e) {
                log.error("[Upload] publish ingest message failed, fallback to sync. doc={}", doc.getId(), e);
                doc.setParseStatus("PARSING");
                documentMapper.updateById(doc);
                indexingService.indexDocument(doc, content);
            }
        } else {
            // 同步索引（M1 简化 / 兼容无 RabbitMQ 环境）
            indexingService.indexDocument(doc, content);
        }
        return documentMapper.selectById(doc.getId());
    }

    @Override
    public KnowledgeDocument uploadFile(Long kbId, MultipartFile file) {
        kbService.assertWritable(kbId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (!objectStorage.isEnabled()) {
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE,
                    "对象存储未启用，请在 application-dev.yaml 里设置 zwsk.storage.minio.enabled=true 后重试");
        }
        String origName = file.getOriginalFilename();
        if (origName == null || origName.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件名为空");
        }
        String name = origName.trim();
        String lower = name.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot) : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型: " + ext);
        }
        long maxSize = minioProps.getMaxFileSize() > 0 ? minioProps.getMaxFileSize() : DEFAULT_MAX_FILE_SIZE;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE,
                    "文件超过上限 " + (maxSize / 1024 / 1024) + " MB");
        }

        // 1. 计算 sha256（一次读完可接受；外部 multipart 已在 tomcat 缓冲）
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取上传流失败: " + e.getMessage());
        }
        String hash = sha256(bytes);

        // 2. 同 KB + 同 hash 去重
        KnowledgeDocument exists = documentMapper.selectOne(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKbId, kbId)
                .eq(KnowledgeDocument::getFileHash, hash)
                .last("LIMIT 1"));
        if (exists != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文档已存在（hash 相同），id=" + exists.getId());
        }

        Long tenantId = SecurityUtils.currentUser().getTenantId();

        // 3. 上传 MinIO（失败不起 DB 记录）
        String objectKey = objectKeyBuilder.build(tenantId, kbId, hash, name);
        try (InputStream in = file.getInputStream()) {
            objectStorage.upload(objectKey, in, bytes.length, file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "存入对象存储失败: " + e.getMessage());
        }

        // 4. 写 DB
        boolean async = ingestProps.getAsync().isEnabled();
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setFileName(name);
        doc.setFileType(ext.startsWith(".") ? ext.substring(1) : ext);
        doc.setFileSize((long) bytes.length);
        doc.setFileHash(hash);
        doc.setFileUrl("minio://" + objectKey);
        doc.setParseStatus(async ? "PENDING" : "PARSING");
        doc.setUploaderId(SecurityUtils.currentUserId());
        doc.setChunkCount(0);
        try {
            documentMapper.insert(doc);
        } catch (RuntimeException e) {
            // DB 插入失败，回滚 MinIO 对象（幂等）
            objectStorage.delete(objectKey);
            throw e;
        }

        // 5. 发 ingest 消息：Listener 会拉对象 + Tika 解析
        if (async) {
            try {
                ingestProducer.publishObject(doc.getId(), tenantId, objectKey, name);
            } catch (Exception e) {
                log.error("[Upload] publish object ingest failed, fallback to sync. doc={}", doc.getId(), e);
                doc.setParseStatus("PARSING");
                documentMapper.updateById(doc);
                indexInline(doc, objectKey, name);
            }
        } else {
            indexInline(doc, objectKey, name);
        }
        return documentMapper.selectById(doc.getId());
    }

    /**
     * 同步路径（或 publish 失败 fallback）：当前线程下载对象 + Tika 解析 + 索引。
     *
     * <p><b>设计取舍 / 风险提示：</b>该方法在 HTTP 上传请求线程内同步跑完整索引流水线
     *（下载、Tika 解析、Embedding API 调用、Qdrant upsert、DB 落库）。对于大文件或
     * 慢 Embedding 服务，单次调用可能持续数十秒甚至更久，期间会一直占用 Tomcat 工作线程，
     * 上传连接也无法释放。</p>
     *
     * <p><b>使用约束：</b>
     * <ul>
     *   <li>仅作为 {@code zwsk.rag.ingest.async.enabled=false} 或 RabbitMQ publish 失败时的兜底；</li>
     *   <li>生产环境强烈建议保持 async 默认开启，让 ingest listener 在独立消费者线程池里跑；</li>
     *   <li>文件大小已经在 {@code uploadFile} 入口被
     *       {@link com.zwskchat.zwskchat.config.properties.MinioProperties#getMaxFileSize()}
     *       限制（默认 ~10MB），间接控制了同步路径的最坏耗时；</li>
     *   <li>若需要再次提高文件上限，请优先把 ingest async 强制启用，或改为投递独立 executor。</li>
     * </ul>
     * </p>
     */
    private void indexInline(KnowledgeDocument doc, String objectKey, String fileName) {
        long t0 = System.currentTimeMillis();
        log.info("[Upload] inline index begin doc={} size={}B type={} —— 同步占用 HTTP 线程，仅适用兜底场景",
                doc.getId(), doc.getFileSize(), doc.getFileType());
        try (InputStream in = objectStorage.openStream(objectKey)) {
            // 如果是 PDF，读取字节用于按页切分
            if ("pdf".equalsIgnoreCase(doc.getFileType())) {
                byte[] pdfBytes = in.readAllBytes();
                indexingService.indexDocument(doc, null, pdfBytes);
            } else {
                String text = documentParser.parse(in, fileName);
                indexingService.indexDocument(doc, text, null);
            }
            long cost = System.currentTimeMillis() - t0;
            if (cost > 5000) {
                log.warn("[Upload] inline index slow doc={} costMs={} —— 建议启用 ingest async 模式",
                        doc.getId(), cost);
            } else {
                log.info("[Upload] inline index done doc={} costMs={}", doc.getId(), cost);
            }
        } catch (IOException e) {
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "同步索引读流失败: " + e.getMessage());
        }
    }

    @Override
    public List<KnowledgeDocument> listByKb(Long kbId) {
        kbService.assertReadable(kbId);
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getKbId, kbId)
                .orderByDesc(KnowledgeDocument::getCreateTime));
    }

    @Override
    public KnowledgeDocument updateGovernance(Long docId, UpdateDocumentGovernanceRequest req) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        // 与删除一致的权限：仅上传者 / 知识库拥有者 / 管理员可标注治理元数据
        Long me = SecurityUtils.currentUserId();
        boolean isUploader = doc.getUploaderId() != null && doc.getUploaderId().equals(me);
        boolean isKbOwnerOrAdmin = false;
        try {
            kbService.assertWritable(doc.getKbId());
            isKbOwnerOrAdmin = true;
        } catch (BusinessException ignore) {
            // 不是 kb owner / admin
        }
        if (!isUploader && !isKbOwnerOrAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅上传者 / 知识库拥有者 / 管理员可标注该文档");
        }

        // 仅更新非空字段（PATCH 语义）
        KnowledgeDocument patch = new KnowledgeDocument();
        patch.setId(docId);
        if (req.getAssetType() != null) patch.setAssetType(req.getAssetType());
        if (req.getDocVersion() != null) patch.setDocVersion(req.getDocVersion());
        if (req.getIndustry() != null) patch.setIndustry(req.getIndustry());
        if (req.getScenario() != null) patch.setScenario(req.getScenario());
        if (req.getAuthorityLevel() != null) patch.setAuthorityLevel(req.getAuthorityLevel());
        if (req.getConfidentialityLevel() != null) patch.setConfidentialityLevel(req.getConfidentialityLevel());
        if (req.getAccessScope() != null) patch.setAccessScope(req.getAccessScope());
        if (req.getFreshnessDate() != null) patch.setFreshnessDate(req.getFreshnessDate());
        if (req.getCitationText() != null) patch.setCitationText(req.getCitationText());
        if (req.getSupersedesDocId() != null) {
            if (req.getSupersedesDocId().equals(docId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "supersedesDocId 不能指向文档自身");
            }
            // 校验被取代的旧版本存在且与本文档同属一个知识库，避免跨库/跨租户串接版本链
            KnowledgeDocument prev = documentMapper.selectById(req.getSupersedesDocId());
            if (prev == null || !prev.getKbId().equals(doc.getKbId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "supersedesDocId 指向的旧版本不存在或不属于同一知识库");
            }
            patch.setSupersedesDocId(req.getSupersedesDocId());
        }
        documentMapper.updateById(patch);

        // 同步冗余到 chunk（资产类型 / 权威等级 / 密级 / 时效），使评分立即用上真实信号。
        // 只 set 本次提交的字段，未提交的保持原值。
        var chunkUpdate = Wrappers.<KnowledgeChunk>lambdaUpdate()
                .eq(KnowledgeChunk::getDocumentId, docId);
        boolean anyChunkField = false;
        if (req.getAssetType() != null) {
            chunkUpdate.set(KnowledgeChunk::getAssetType, req.getAssetType());
            anyChunkField = true;
        }
        if (req.getAuthorityLevel() != null) {
            chunkUpdate.set(KnowledgeChunk::getAuthorityLevel, req.getAuthorityLevel());
            anyChunkField = true;
        }
        if (req.getConfidentialityLevel() != null) {
            chunkUpdate.set(KnowledgeChunk::getConfidentialityLevel, req.getConfidentialityLevel());
            anyChunkField = true;
        }
        if (req.getFreshnessDate() != null) {
            chunkUpdate.set(KnowledgeChunk::getFreshnessDate, req.getFreshnessDate());
            anyChunkField = true;
        }
        if (anyChunkField) {
            chunkMapper.update(null, chunkUpdate);
        }

        return documentMapper.selectById(docId);
    }

    @Override
    public void delete(Long docId) {
        KnowledgeDocument doc = documentMapper.selectById(docId);
        if (doc == null) throw new BusinessException(ResultCode.NOT_FOUND, "文档不存在");
        Long me = SecurityUtils.currentUserId();
        boolean isUploader = doc.getUploaderId() != null && doc.getUploaderId().equals(me);
        boolean isKbOwnerOrAdmin = false;
        try {
            kbService.assertWritable(doc.getKbId());
            isKbOwnerOrAdmin = true;
        } catch (BusinessException ignore) {
            // 不是 kb owner / admin
        }
        if (!isUploader && !isKbOwnerOrAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅上传者 / 知识库拥有者 / 管理员可删除该文档");
        }
        // 1. 软删 chunk 行；Qdrant 暂留作为孤儿（M2 异步清理）
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery().eq(KnowledgeChunk::getDocumentId, docId));
        // 2. 如果是 MinIO 背后的文档，顺带删对象（幂等）
        if (doc.getFileUrl() != null && doc.getFileUrl().startsWith("minio://")) {
            String key = doc.getFileUrl().substring("minio://".length());
            objectStorage.delete(key);
        }
        documentMapper.deleteById(docId);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
