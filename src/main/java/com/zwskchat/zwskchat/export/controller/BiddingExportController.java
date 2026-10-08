package com.zwskchat.zwskchat.export.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Tag(name = "标书导出中心")
@Slf4j
@RestController
@RequestMapping("/bidding")
public class BiddingExportController {

    @Operation(summary = "导出兴天智能标书Word成果件")
    @PostMapping("/export")
    public void exportWord(@RequestBody BiddingExportRequest request, HttpServletResponse response) {
        log.info("[BiddingExport] exporting document: title={}, budget={}", request.getTitle(), request.getBudget());
        
        try (XWPFDocument doc = new XWPFDocument()) {
            // 1. 封面：兴天红头大标题
            XWPFParagraph header = doc.createParagraph();
            header.setAlignment(ParagraphAlignment.CENTER);
            header.setSpacingBefore(300);
            XWPFRun headerRun = header.createRun();
            headerRun.setText("四川兴天建设项目管理有限公司");
            headerRun.setBold(true);
            headerRun.setFontSize(24);
            headerRun.setColor("C00000"); // 兴天标志深红色
            headerRun.setFontFamily("Microsoft YaHei");
            
            XWPFParagraph subHeader = doc.createParagraph();
            subHeader.setAlignment(ParagraphAlignment.CENTER);
            subHeader.setSpacingAfter(400); // 间隔
            XWPFRun subRun = subHeader.createRun();
            subRun.setText("SICHUAN XINGTIAN PROJECT MANAGEMENT CO., LTD.");
            subRun.setFontSize(10);
            subRun.setColor("888888");
            subRun.setFontFamily("Arial");

            // 2. 标书基本元数据表格
            XWPFTable table = doc.createTable(4, 2);
            table.setWidth("100%");
            setCellText(table.getRow(0).getCell(0), "项目名称", true, "F2F2F2");
            setCellText(table.getRow(0).getCell(1), request.getTitle(), false, null);
            
            setCellText(table.getRow(1).getCell(0), "招标范围", true, "F2F2F2");
            setCellText(table.getRow(1).getCell(1), request.getScope() != null ? request.getScope() : "工程咨询、项目监理与全过程造识审核", false, null);
            
            setCellText(table.getRow(2).getCell(0), "项目概算", true, "F2F2F2");
            setCellText(table.getRow(2).getCell(1), request.getBudget() != null ? request.getBudget() : "详见招标文件", false, null);
            
            setCellText(table.getRow(3).getCell(0), "编制日期", true, "F2F2F2");
            setCellText(table.getRow(3).getCell(1), LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), false, null);
            
            // 分割线
            XWPFParagraph divider = doc.createParagraph();
            divider.setSpacingAfter(300);

            // 3. 解析 Markdown 格式的标书正文大纲并写入 Word
            String content = request.getContent();
            if (content != null && !content.isBlank()) {
                String[] lines = content.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setFontFamily("SimSun"); // 中文宋体

                    if (line.startsWith("# ")) {
                        // 一级标题
                        p.setSpacingBefore(200);
                        p.setSpacingAfter(100);
                        r.setText(line.substring(2));
                        r.setBold(true);
                        r.setFontSize(16);
                        r.setFontFamily("Microsoft YaHei");
                    } else if (line.startsWith("## ")) {
                        // 二级标题
                        p.setSpacingBefore(150);
                        p.setSpacingAfter(80);
                        r.setText(line.substring(3));
                        r.setBold(true);
                        r.setFontSize(13);
                        r.setFontFamily("Microsoft YaHei");
                    } else if (line.startsWith("### ")) {
                        // 三级标题
                        p.setSpacingBefore(100);
                        p.setSpacingAfter(60);
                        r.setText(line.substring(4));
                        r.setBold(true);
                        r.setFontSize(11);
                        r.setFontFamily("Microsoft YaHei");
                    } else if (line.startsWith("- ") || line.startsWith("* ")) {
                        // 列表项
                        p.setIndentationLeft(360); // 缩进
                        r.setText("• " + line.substring(2));
                        r.setFontSize(10.5);
                    } else if (line.matches("^\\d+\\.\\s+.*")) {
                        // 数字编号列表
                        p.setIndentationLeft(360);
                        r.setText(line);
                        r.setFontSize(10.5);
                    } else {
                        // 普通段落
                        p.setSpacingAfter(120);
                        p.setIndentationFirstLine(440); // 首行缩进 2 字符
                        r.setText(line);
                        r.setFontSize(10.5);
                    }
                }
            } else {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText("（暂无具体大纲，请在 AI 聊天中生成后重试导出）");
                r.setFontSize(10.5);
            }

            // 4. 输出流返回给前端
            String filename = "兴天智能标书成果_" + request.getTitle().replace(" ", "_") + ".docx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
            
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename);
            
            try (OutputStream os = response.getOutputStream()) {
                doc.write(os);
                os.flush();
            }
            log.info("[BiddingExport] document exported successfully!");
        } catch (Exception e) {
            log.error("[BiddingExport] failed to export document", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void setCellText(XWPFTableCell cell, String text, boolean isHeader, String bgColor) {
        XWPFParagraph p = cell.getParagraphs().get(0);
        if (p == null) {
            p = cell.addParagraph();
        }
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setFontFamily("SimSun");
        r.setFontSize(10.5);
        if (isHeader) {
            r.setBold(true);
            p.setAlignment(ParagraphAlignment.CENTER);
        }
        if (bgColor != null) {
            cell.setColor(bgColor);
        }
    }

    @Data
    public static class BiddingExportRequest {
        private String title;
        private String scope;
        private String budget;
        private String content;
    }
}
