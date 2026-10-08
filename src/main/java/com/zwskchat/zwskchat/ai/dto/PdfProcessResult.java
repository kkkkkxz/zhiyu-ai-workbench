package com.zwskchat.zwskchat.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * PDF 处理结果
 */
@Data
@Builder
public class PdfProcessResult {
    /** 总页数 */
    private int pageCount;

    /** 是否使用文本模式 */
    private boolean prefill;

    /** 文本模式：每页的文本内容 */
    private List<String> pageTexts;

    /** 图片模式：每页的 Base64 编码图片 */
    private List<String> pageImages;
}
