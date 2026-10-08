package com.zwskchat.zwskchat.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.io.InputStream;

/**
 * PDF 处理请求
 */
@Data
@Builder
public class PdfProcessRequest {
    /** PDF 文件输入流 */
    private InputStream inputStream;

    /** 是否使用文本模式（true=文本，false=图片） */
    private boolean prefill;

    /** 可选：系统提示词 */
    private String systemPrompt;

    /** 可选：DPI 设置（仅图片模式有效，默认 150） */
    @Builder.Default
    private int dpi = 150;
}
