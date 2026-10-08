package com.zwskchat.zwskchat.kb.parser;

import java.io.InputStream;

/**
 * 文档解析器：将各种二进制格式（PDF / DOCX / PPTX / HTML / RTF / XLSX / TXT / MD ...）
 * 抽取为可索引的纯文本。
 *
 * <p>当前实现 {@link TikaDocumentParser} 用 Apache Tika {@code AutoDetectParser}，
 * 一站式覆盖 1000+ 文件类型；后续可拆分为细粒度 SPI（PDF / Office / OCR 各一）。</p>
 */
public interface DocumentParser {

    /**
     * 抽取纯文本。
     *
     * @param in       字节流；调用方负责关闭
     * @param fileName 原始文件名（含扩展名），用作格式探测的兜底信号
     * @return 抽取出的纯文本（已去除 BOM / 多余空白；可能为空字符串）
     * @throws ParseException 解析失败（封装 Tika SAXException / IOException 等）
     */
    String parse(InputStream in, String fileName) throws ParseException;

    /** 文档解析失败统一异常。 */
    class ParseException extends RuntimeException {
        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
