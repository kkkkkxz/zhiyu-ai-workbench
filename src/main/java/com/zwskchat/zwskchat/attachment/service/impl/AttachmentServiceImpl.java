package com.zwskchat.zwskchat.attachment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.attachment.dto.AttachmentDownloadResponse;
import com.zwskchat.zwskchat.attachment.entity.BizAttachment;
import com.zwskchat.zwskchat.attachment.mapper.BizAttachmentMapper;
import com.zwskchat.zwskchat.attachment.service.AttachmentAnalysisService;
import com.zwskchat.zwskchat.attachment.service.AttachmentPermissionService;
import com.zwskchat.zwskchat.attachment.service.AttachmentService;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.zwskchat.zwskchat.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int URL_EXPIRES_IN_SECONDS = 600;
    private static final DateTimeFormatter DATE_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".txt", ".md", ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".csv",
            ".ppt", ".pptx", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".zip");

    private final BizAttachmentMapper attachmentMapper;
    private final AttachmentPermissionService permissionService;
    private final AttachmentAnalysisService analysisService;
    private final ObjectStorage objectStorage;

    @Override
    public List<BizAttachment> listByBiz(String bizType, Long bizId) {
        permissionService.assertReadable(bizType, bizId);
        return attachmentMapper.selectList(Wrappers.<BizAttachment>lambdaQuery()
                .eq(BizAttachment::getBizType, bizType)
                .eq(BizAttachment::getBizId, bizId)
                .orderByDesc(BizAttachment::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizAttachment upload(String bizType, Long bizId, String category, MultipartFile file) {
        permissionService.assertWritable(bizType, bizId);
        validateFile(file);
        if (!objectStorage.isEnabled()) {
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "对象存储未启用，无法上传业务附件");
        }

        LoginUser user = SecurityUtils.currentUser();
        byte[] bytes = readBytes(file);
        String hash = sha256(bytes);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String fileType = extensionOf(fileName);
        String objectKey = buildObjectKey(user.getTenantId(), bizType, bizId, hash, fileName);

        try (InputStream in = file.getInputStream()) {
            objectStorage.upload(objectKey, in, bytes.length, file.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "读取上传文件失败: " + e.getMessage());
        }

        BizAttachment attachment = new BizAttachment();
        attachment.setTenantId(user.getTenantId());
        attachment.setBizType(bizType);
        attachment.setBizId(bizId);
        attachment.setCategory(normalizeCategory(category));
        attachment.setFileName(fileName);
        attachment.setFileKey(objectKey);
        attachment.setFileType(fileType);
        attachment.setContentType(file.getContentType());
        attachment.setFileSize((long) bytes.length);
        attachment.setFileHash(hash);
        attachment.setVersionNo(nextVersionNo(bizType, bizId, fileName));
        attachment.setSource("manual");
        attachment.setVisibility("internal");
        attachment.setUploaderId(user.getUserId());
        try {
            attachmentMapper.insert(attachment);
        } catch (RuntimeException e) {
            objectStorage.delete(objectKey);
            throw e;
        }
        BizAttachment saved = attachmentMapper.selectById(attachment.getId());
        analysisService.requestAnalysisOnUpload(saved, user);
        return saved;
    }

    @Override
    public AttachmentDownloadResponse createDownloadUrl(Long attachmentId) {
        BizAttachment attachment = getAttachment(attachmentId);
        permissionService.assertReadable(attachment.getBizType(), attachment.getBizId());
        String url = objectStorage.presignedDownloadUrl(attachment.getFileKey(), URL_EXPIRES_IN_SECONDS);
        return new AttachmentDownloadResponse(url, URL_EXPIRES_IN_SECONDS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long attachmentId) {
        BizAttachment attachment = getAttachment(attachmentId);
        permissionService.assertWritable(attachment.getBizType(), attachment.getBizId());
        analysisService.deleteByAttachment(attachmentId);
        attachmentMapper.deleteById(attachmentId);
        objectStorage.delete(attachment.getFileKey());
    }

    private BizAttachment getAttachment(Long attachmentId) {
        if (attachmentId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "附件 ID 不能为空");
        }
        BizAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "附件不存在");
        }
        return attachment;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.FILE_TOO_LARGE, "文件超过 50MB");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_ALLOWED, "不支持的附件类型: " + ext);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "读取上传文件失败: " + e.getMessage());
        }
    }

    private int nextVersionNo(String bizType, Long bizId, String fileName) {
        Long count = attachmentMapper.selectCount(Wrappers.<BizAttachment>lambdaQuery()
                .eq(BizAttachment::getBizType, bizType)
                .eq(BizAttachment::getBizId, bizId)
                .eq(BizAttachment::getFileName, fileName));
        return count == null ? 1 : count.intValue() + 1;
    }

    private String buildObjectKey(Long tenantId, String bizType, Long bizId, String hash, String fileName) {
        String hash8 = hash.substring(0, Math.min(8, hash.length()));
        return "biz/" + tenantId + "/" + bizType + "/" + bizId + "/"
                + LocalDate.now().format(DATE_PATH_FORMAT) + "/" + hash8 + "-" + fileName;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "general";
        }
        return category.trim();
    }

    private String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "untitled";
        }
        String fileName = originalFilename.trim().replaceAll("[\\\\/\\u0000-\\u001F]", "_");
        if (fileName.length() > 180) {
            int dot = fileName.lastIndexOf('.');
            String ext = dot > 0 && dot > fileName.length() - 12 ? fileName.substring(dot) : "";
            return fileName.substring(0, 180 - ext.length()) + ext;
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        String lower = fileName.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot) : "";
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "计算文件哈希失败");
        }
    }
}
