package com.zwskchat.zwskchat.export.service;

import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.export.dto.ExportReport;
import com.zwskchat.zwskchat.export.dto.ExportSection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExportDocumentRenderer {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME = "application/pdf";
    private static final String CHINESE_FONT_PATH = "C:\\Windows\\Fonts\\simhei.ttf";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public byte[] render(ExportReport report, String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        try {
            if ("docx".equals(normalized)) {
                return renderDocx(report);
            }
            if ("pdf".equals(normalized)) {
                return renderPdf(report);
            }
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的导出格式: " + format);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "导出文件生成失败: " + e.getMessage());
        }
    }

    public String contentType(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("docx".equals(normalized)) {
            return DOCX_MIME;
        }
        if ("pdf".equals(normalized)) {
            return PDF_MIME;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的导出格式: " + format);
    }

    public String extension(String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase();
        if ("docx".equals(normalized)) {
            return "docx";
        }
        if ("pdf".equals(normalized)) {
            return "pdf";
        }
        throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的导出格式: " + format);
    }

    private byte[] renderDocx(ExportReport report) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeDocxTitle(document, report);
            if (report.getSubtitle() != null && !report.getSubtitle().isBlank()) {
                XWPFParagraph subtitle = document.createParagraph();
                subtitle.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun subtitleRun = subtitle.createRun();
                subtitleRun.setText(report.getSubtitle());
                subtitleRun.setFontSize(10);
                subtitleRun.setFontFamily("Microsoft YaHei");
                subtitleRun.setColor("666666");
            }
            if (report.getSections() != null) {
                for (ExportSection section : report.getSections()) {
                    writeDocxSection(document, section);
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private void writeDocxTitle(XWPFDocument document, ExportReport report) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setFontFamily("Microsoft YaHei");
        titleRun.setText(report.getTitle() == null ? "导出报告" : report.getTitle());
    }

    private void writeDocxSection(XWPFDocument document, ExportSection section) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(120);
        heading.setSpacingAfter(80);
        XWPFRun headingRun = heading.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(12);
        headingRun.setFontFamily("Microsoft YaHei");
        headingRun.setText(section.getTitle());

        List<String> lines = section.getLines();
        if (lines == null || lines.isEmpty()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontSize(10);
            run.setText("暂无内容");
            return;
        }
        for (String line : lines) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingAfter(40);
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("Microsoft YaHei");
            run.setFontSize(10);
            run.setText(line);
        }
    }

    private byte[] renderPdf(ExportReport report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            try (PdfCursor cursor = new PdfCursor(document, font)) {
                cursor.writeCenter(report.getTitle() == null ? "导出报告" : report.getTitle(), 18f);
                if (report.getSubtitle() != null && !report.getSubtitle().isBlank()) {
                    cursor.writeCenter(report.getSubtitle(), 10f);
                }
                if (report.getSections() != null) {
                    for (ExportSection section : report.getSections()) {
                        cursor.writeSection(section);
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        File fontFile = Path.of(CHINESE_FONT_PATH).toFile();
        if (!fontFile.exists()) {
            throw new IOException("缺少中文字体文件: " + CHINESE_FONT_PATH);
        }
        return PDType0Font.load(document, fontFile);
    }

    private final class PdfCursor implements AutoCloseable {

        private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
        private static final float LEFT = 50f;
        private static final float RIGHT = 50f;
        private static final float TOP = 50f;
        private static final float BOTTOM = 50f;
        private static final float LINE_GAP = 6f;

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream contentStream;
        private float cursorY;

        private PdfCursor(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true);
            cursorY = PAGE_HEIGHT - TOP;
        }

        private void closeStream() throws IOException {
            if (contentStream != null) {
                contentStream.close();
                contentStream = null;
            }
        }

        private void ensureSpace(float height) throws IOException {
            if (cursorY - height < BOTTOM) {
                newPage();
            }
        }

        private void writeCenter(String text, float fontSize) throws IOException {
            List<String> lines = wrap(text, font, fontSize, PAGE_WIDTH - LEFT - RIGHT);
            for (String line : lines) {
                ensureSpace(fontSize + LINE_GAP);
                contentStream.beginText();
                contentStream.setFont(font, fontSize);
                float width = font.getStringWidth(line) / 1000f * fontSize;
                float x = LEFT + Math.max(0f, (PAGE_WIDTH - LEFT - RIGHT - width) / 2f);
                contentStream.newLineAtOffset(x, cursorY);
                contentStream.showText(line);
                contentStream.endText();
                cursorY -= fontSize + LINE_GAP;
            }
            cursorY -= 6f;
        }

        private void writeSection(ExportSection section) throws IOException {
            ensureSpace(24f);
            contentStream.beginText();
            contentStream.setFont(font, 12f);
            contentStream.newLineAtOffset(LEFT, cursorY);
            contentStream.showText(section.getTitle() == null ? "未命名章节" : section.getTitle());
            contentStream.endText();
            cursorY -= 18f;

            List<String> lines = section.getLines();
            if (lines == null || lines.isEmpty()) {
                writeBodyLine("暂无内容", 10f);
                cursorY -= 4f;
                return;
            }
            for (String line : lines) {
                writeBodyBlock(line, 10f);
            }
            cursorY -= 4f;
        }

        private void writeBodyBlock(String text, float fontSize) throws IOException {
            List<String> lines = wrap(text == null ? "" : text, font, fontSize, PAGE_WIDTH - LEFT - RIGHT);
            for (String line : lines) {
                writeBodyLine(line, fontSize);
            }
        }

        private void writeBodyLine(String line, float fontSize) throws IOException {
            ensureSpace(fontSize + LINE_GAP);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(LEFT, cursorY);
            contentStream.showText(line);
            contentStream.endText();
            cursorY -= fontSize + LINE_GAP;
        }

        private List<String> wrap(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isBlank()) {
                lines.add("");
                return lines;
            }
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\n') {
                    lines.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(ch);
                float width = font.getStringWidth(current.toString()) / 1000f * fontSize;
                if (width > maxWidth) {
                    char last = current.charAt(current.length() - 1);
                    current.setLength(current.length() - 1);
                    if (!current.isEmpty()) {
                        lines.add(current.toString());
                    }
                    current.setLength(0);
                    if (!Character.isWhitespace(last)) {
                        current.append(last);
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
            return lines;
        }

        @Override
        public void close() throws IOException {
            closeStream();
        }
    }

    public String formatValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof LocalDate date) {
            return DATE_FORMATTER.format(date);
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME_FORMATTER.format(dateTime);
        }
        return String.valueOf(value);
    }
}
