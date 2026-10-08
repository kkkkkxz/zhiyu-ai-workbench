package com.zwskchat.zwskchat.attachment.service;

import com.zwskchat.zwskchat.attachment.dto.AttachmentDownloadResponse;
import com.zwskchat.zwskchat.attachment.entity.BizAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {

    List<BizAttachment> listByBiz(String bizType, Long bizId);

    BizAttachment upload(String bizType, Long bizId, String category, MultipartFile file);

    AttachmentDownloadResponse createDownloadUrl(Long attachmentId);

    void delete(Long attachmentId);
}
