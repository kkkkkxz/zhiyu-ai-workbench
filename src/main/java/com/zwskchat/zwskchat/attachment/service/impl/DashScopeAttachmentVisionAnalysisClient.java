package com.zwskchat.zwskchat.attachment.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.attachment.entity.BizAttachment;
import com.zwskchat.zwskchat.attachment.service.AttachmentVisionAnalysisClient;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.storage.ObjectStorage;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeAttachmentVisionAnalysisClient implements AttachmentVisionAnalysisClient {

    private static final int PDF_DPI = 140;
    private static final String MIME_PNG = "image/png";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_WEBP = "image/webp";
    private static final String MIME_GIF = "image/gif";

    private final AiProperties aiProperties;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;
    private final TenantAiConfigService tenantAiConfigService;

    @Override
    public String analyze(BizAttachment attachment, String prompt) {
        AiProperties.DashScope dashScope = aiProperties.getDashscope();
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = resolveApiKey(dashScope, tenantConfig);
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "DashScope API Key 未配置，无法解析图片附件");
        }
        List<String> imageDataUrls = loadImageDataUrls(attachment, dashScope.getAnalysisMaxImages());
        if (imageDataUrls.isEmpty()) {
            throw new BusinessException(ResultCode.DOC_PARSE_FAILED, "未能从附件中生成可识别图片");
        }
        return callVisionModel(dashScope, tenantConfig, imageDataUrls, prompt);
    }

    private List<String> loadImageDataUrls(BizAttachment attachment, int maxImages) {
        String extension = extensionOf(attachment.getFileName());
        if (".pdf".equals(extension)) {
            return renderPdfPages(attachment, maxImages);
        }
        String mimeType = resolveMimeType(extension, attachment.getContentType());
        try (InputStream inputStream = objectStorage.openStream(attachment.getFileKey())) {
            byte[] bytes = inputStream.readAllBytes();
            return List.of("data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes));
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED, "读取图片附件失败: " + e.getMessage());
        }
    }

    private List<String> renderPdfPages(BizAttachment attachment, int maxImages) {
        try (InputStream inputStream = objectStorage.openStream(attachment.getFileKey());
             PDDocument document = PDDocument.load(inputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageLimit = Math.min(document.getNumberOfPages(), Math.max(1, maxImages));
            List<String> images = new ArrayList<>(pageLimit);
            for (int index = 0; index < pageLimit; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, PDF_DPI, ImageType.RGB);
                images.add("data:" + MIME_PNG + ";base64," + encodePng(image));
            }
            return images;
        } catch (java.io.IOException e) {
            throw new BusinessException(ResultCode.DOC_PARSE_FAILED, "渲染扫描 PDF 失败: " + e.getMessage());
        }
    }

    private String callVisionModel(AiProperties.DashScope dashScope,
                                   TenantAiRuntimeConfig tenantConfig,
                                   List<String> imageDataUrls,
                                   String prompt) {
        try {
            String model = tenantConfig != null && tenantConfig.getVisionModel() != null
                    && !tenantConfig.getVisionModel().isBlank()
                    ? tenantConfig.getVisionModel()
                    : dashScope.getVisionModel();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", 0.1);
            body.put("max_tokens", aiProperties.getMaxOutputTokens());
            body.put("messages", List.of(buildUserMessage(imageDataUrls, prompt)));

            HttpRequest request = HttpRequest.newBuilder(URI.create(buildUrl(dashScope, tenantConfig)))
                    .timeout(Duration.ofSeconds(Math.max(30, dashScope.getAnalysisTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolveApiKey(dashScope, tenantConfig))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("[AttachmentVision] non-2xx status={} body={}", response.statusCode(), truncate(response.body()));
                throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE,
                        "图片附件 AI 解析失败: HTTP " + response.statusCode());
            }
            return extractContent(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new BusinessException(ResultCode.AI_TIMEOUT, "图片附件 AI 解析超时");
        } catch (Exception e) {
            log.error("[AttachmentVision] call failed", e);
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "图片附件 AI 解析失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildUserMessage(List<String> imageDataUrls, String prompt) {
        List<Map<String, Object>> content = new ArrayList<>(imageDataUrls.size() + 1);
        content.add(Map.of("type", "text", "text", prompt));
        for (String imageDataUrl : imageDataUrls) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", imageDataUrl)));
        }
        return Map.of("role", "user", "content", content);
    }

    private String extractContent(String responseBody) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new BusinessException(ResultCode.AI_SERVICE_UNAVAILABLE, "图片附件 AI 未返回内容");
        }
        return content;
    }

    private String buildUrl(AiProperties.DashScope dashScope, TenantAiRuntimeConfig tenantConfig) {
        String baseUrl = tenantConfig != null && tenantConfig.getBaseUrl() != null
                && !tenantConfig.getBaseUrl().isBlank()
                ? tenantConfig.getBaseUrl()
                : dashScope.getCompatibleBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private String resolveApiKey(AiProperties.DashScope dashScope, TenantAiRuntimeConfig tenantConfig) {
        if (tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()) {
            return tenantConfig.getApiKey();
        }
        return dashScope.getApiKey();
    }

    private String encodePng(BufferedImage image) throws java.io.IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private String resolveMimeType(String extension, String contentType) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return contentType;
        }
        return switch (extension) {
            case ".jpg", ".jpeg" -> MIME_JPEG;
            case ".webp" -> MIME_WEBP;
            case ".gif" -> MIME_GIF;
            default -> MIME_PNG;
        };
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        return dot >= 0 ? lower.substring(dot) : "";
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }
}
