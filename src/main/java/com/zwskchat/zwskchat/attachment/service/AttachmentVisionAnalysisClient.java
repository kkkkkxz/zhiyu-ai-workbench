package com.zwskchat.zwskchat.attachment.service;

import com.zwskchat.zwskchat.attachment.entity.BizAttachment;

/**
 * 图片/扫描件附件的视觉识别与业务判断客户端。
 */
public interface AttachmentVisionAnalysisClient {

    /**
     * 对图片或扫描 PDF 进行视觉解析，并返回符合附件解析 JSON 结构的模型输出。
     *
     * @param attachment 业务附件
     * @param prompt 业务判断提示词
     * @return 模型原始 JSON 文本
     */
    String analyze(BizAttachment attachment, String prompt);
}
