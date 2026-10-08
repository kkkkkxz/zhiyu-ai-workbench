package com.zwskchat.zwskchat.ai;

import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.dto.PdfProcessRequest;
import com.zwskchat.zwskchat.ai.dto.PdfProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AI 服务：处理 PDF 文档的文本/图片提取和模型调用。
 *
 * <p>功能：</p>
 * <ul>
 *   <li>prefill=true：提取 PDF 文本，按页切片，使用文本模型处理（低成本）</li>
 *   <li>prefill=false：将 PDF 页面渲染为图片，使用视觉模型处理（高成本）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiChatClient chatClient;

    /**
     * 处理 PDF 文档
     *
     * @param request 包含 PDF 输入流、prefill 标志等参数
     * @return 处理结果，包含每页的文本或图片数据
     */
    public PdfProcessResult processPdf(PdfProcessRequest request) {
        try (PDDocument document = PDDocument.load(request.getInputStream())) {
            int pageCount = document.getNumberOfPages();
            log.info("[AiService] Processing PDF: {} pages, prefill={}", pageCount, request.isPrefill());

            if (request.isPrefill()) {
                return processPdfAsText(document, pageCount);
            } else {
                return processPdfAsImages(document, pageCount, request.getDpi());
            }
        } catch (Exception e) {
            log.error("[AiService] Failed to process PDF", e);
            throw new RuntimeException("PDF 处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * prefill=true：提取文本，按页切片
     */
    private PdfProcessResult processPdfAsText(PDDocument document, int pageCount) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        List<String> pageTexts = new ArrayList<>(pageCount);

        for (int i = 1; i <= pageCount; i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document);
            pageTexts.add(pageText != null ? pageText.trim() : "");
        }

        log.info("[AiService] Extracted text from {} pages", pageCount);
        return PdfProcessResult.builder()
                .pageCount(pageCount)
                .prefill(true)
                .pageTexts(pageTexts)
                .build();
    }

    /**
     * prefill=false：渲染为图片
     */
    private PdfProcessResult processPdfAsImages(PDDocument document, int pageCount, int dpi) throws Exception {
        PDFRenderer renderer = new PDFRenderer(document);
        List<String> pageImages = new ArrayList<>(pageCount);

        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, dpi);
            String base64Image = encodeImageToBase64(image);
            pageImages.add(base64Image);
        }

        log.info("[AiService] Rendered {} pages to images (DPI={})", pageCount, dpi);
        return PdfProcessResult.builder()
                .pageCount(pageCount)
                .prefill(false)
                .pageImages(pageImages)
                .build();
    }

    /**
     * 将 BufferedImage 编码为 Base64 字符串
     */
    private String encodeImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * 调用 AI 模型处理单个文本片段
     *
     * @param text 文本内容
     * @param systemPrompt 系统提示词
     * @return 模型响应
     */
    public String processTextWithAi(String text, String systemPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(ChatMessage.system(systemPrompt));
        }
        messages.add(ChatMessage.user(text));

        StringBuilder response = new StringBuilder();
        chatClient.streamChat(messages, response::append, usage -> {
            log.debug("[AiService] Token usage: {}", usage);
        });

        return response.toString();
    }

    /**
     * 批量处理文本片段
     *
     * @param texts 文本列表
     * @param systemPrompt 系统提示词
     * @return 处理结果列表
     */
    public List<String> batchProcessTexts(List<String> texts, String systemPrompt) {
        List<String> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            log.info("[AiService] Processing text chunk {}/{}", i + 1, texts.size());
            String result = processTextWithAi(texts.get(i), systemPrompt);
            results.add(result);
        }
        return results;
    }
}
