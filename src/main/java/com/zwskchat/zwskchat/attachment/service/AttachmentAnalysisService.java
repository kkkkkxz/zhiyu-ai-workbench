package com.zwskchat.zwskchat.attachment.service;

import com.zwskchat.zwskchat.attachment.dto.AttachmentAnalysisResponse;
import com.zwskchat.zwskchat.attachment.entity.BizAttachment;
import com.zwskchat.zwskchat.security.LoginUser;

import java.util.List;

public interface AttachmentAnalysisService {

    List<AttachmentAnalysisResponse> listByBiz(String bizType, Long bizId);

    AttachmentAnalysisResponse getByAttachment(Long attachmentId);

    void requestAnalysisOnUpload(BizAttachment attachment, LoginUser user);

    AttachmentAnalysisResponse retry(Long attachmentId);

    void deleteByAttachment(Long attachmentId);
}
