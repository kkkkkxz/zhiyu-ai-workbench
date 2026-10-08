package com.zwskchat.zwskchat.kb.parser;

import com.zwskchat.zwskchat.kb.parser.DocumentParser.ParseException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 专用解析器：按页提取文本，保留页码信息。
 *
 * <p>与 TikaDocumentParser 的区别：</p>
 * <ul>
 *   <li>Tika 返回整个文档的文本，丢失页码信息</li>
 *   <li>此解析器返回每页的文本 + 页码，便于后续切片时保留页码</li>
 * </ul>
 */
@Slf4j
@Component
public class PdfDocumentParser {

    /**
     * 解析 PDF 文档，返回每页的文本和页码
     */
    public ParseResult parseWithPages(InputStream in, String fileName) throws ParseException {
        try (PDDocument document = PDDocument.load(in)) {
            int pageCount = document.getNumberOfPages();
            List<PageContent> pages = new ArrayList<>(pageCount);

            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                String normalized = normalize(pageText);
                pages.add(new PageContent(i, normalized));
            }

            log.debug("[PdfParser] file={} pages={}", fileName, pageCount);
            return new ParseResult(pageCount, pages);

        } catch (Exception e) {
            throw new ParseException("PDF 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 PDF 为单个文本（兼容旧接口）
     */
    public String parse(InputStream in, String fileName) throws ParseException {
        ParseResult result = parseWithPages(in, fileName);
        StringBuilder sb = new StringBuilder();
        for (PageContent page : result.getPages()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(page.getContent());
        }
        return sb.toString();
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw;
        // 去 UTF-8 BOM。改用显式 \uFEFF 字面量，避免源码编码改成 GBK / UTF-16 时编译期识别错。
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
        // 统一换行
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        // 折叠 3 个以上换行为 2 个
        s = s.replaceAll("\n{3,}", "\n\n");
        // 去首尾空白
        return s.trim();
    }

    /**
     * 解析结果
     */
    @Data
    @AllArgsConstructor
    public static class ParseResult {
        private int pageCount;
        private List<PageContent> pages;
    }

    /**
     * 单页内容
     */
    @Data
    @AllArgsConstructor
    public static class PageContent {
        private int pageNumber;
        private String content;
    }
}
