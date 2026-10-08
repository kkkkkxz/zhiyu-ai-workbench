package com.zwskchat.zwskchat.kb.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

/**
 * Apache Tika 实现：自动嗅探格式 + 抽取正文。
 *
 * <p>实现说明：</p>
 * <ul>
 *   <li>用 {@link BodyContentHandler#BodyContentHandler(int)} 限定最大字符数，
 *       默认 5,000,000（约 5MB 文本，对 RAG 已经足够；防止恶意大 PDF 撑爆堆）</li>
 *   <li>统一去 BOM、合并连续空行；保留段落分隔以让 TextSplitter 切片更自然</li>
 *   <li>不输出 Tika metadata，仅正文</li>
 * </ul>
 *
 * <p><b>XXE 安全：</b>Tika 2.x（当前依赖 {@code 2.9.2}）的所有 SAX/DOM/XSLT 解析路径
 * 默认设置 {@code FEATURE_SECURE_PROCESSING} 并禁用外部实体加载（参见
 * {@code org.apache.tika.utils.XMLReaderUtils}）。如果将来升级到不同版本或自行
 * 替换 {@code AutoDetectParser}，务必复测对 OOXML / SVG / RTF 等基于 XML 的文件
 * 不再加载外部实体——可参考 OWASP "XML External Entity Prevention Cheat Sheet"。</p>
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final int MAX_CHARS = 5_000_000;

    @Override
    public String parse(InputStream in, String fileName) throws ParseException {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            if (fileName != null && !fileName.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            }
            ContentHandler handler = new BodyContentHandler(MAX_CHARS);
            ParseContext context = new ParseContext();
            parser.parse(in, handler, metadata, context);
            String raw = handler.toString();
            String normalized = normalize(raw);
            if (log.isDebugEnabled()) {
                log.debug("[Parser] file={} type={} chars={}",
                        fileName, metadata.get(Metadata.CONTENT_TYPE), normalized.length());
            }
            return normalized;
        } catch (Exception e) {
            throw new ParseException("Tika 解析失败: " + e.getMessage(), e);
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw;
        // 去 UTF-8 BOM
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
        // 统一换行
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        // 折叠 3 个以上换行为 2 个
        s = s.replaceAll("\n{3,}", "\n\n");
        // 去首尾空白
        return s.trim();
    }
}
