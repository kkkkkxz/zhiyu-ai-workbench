package com.zwskchat.zwskchat.attachment.dto;

import com.zwskchat.zwskchat.attachment.entity.BizAttachmentAnalysis;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AttachmentAnalysisResponse {
    private Long id;
    private Long attachmentId;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String status;
    private String riskLevel;
    private String documentType;
    private String summary;
    private Map<String, Object> keyFields;
    private List<String> missingFields;
    private List<String> risks;
    private List<String> suggestions;
    private List<String> nextActions;
    private String parseError;
    private Integer parsedTextChars;
    private Long analyzedBy;
    private LocalDateTime analyzedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AttachmentAnalysisResponse from(BizAttachmentAnalysis analysis) {
        AttachmentAnalysisResponse response = new AttachmentAnalysisResponse();
        response.setId(analysis.getId());
        response.setAttachmentId(analysis.getAttachmentId());
        response.setBizType(analysis.getBizType());
        response.setBizId(analysis.getBizId());
        response.setFileName(analysis.getFileName());
        response.setStatus(analysis.getStatus());
        response.setRiskLevel(analysis.getRiskLevel());
        response.setDocumentType(analysis.getDocumentType());
        response.setSummary(analysis.getSummary());
        response.setParseError(analysis.getParseError());
        response.setParsedTextChars(analysis.getParsedTextChars());
        response.setAnalyzedBy(analysis.getAnalyzedBy());
        response.setAnalyzedAt(analysis.getAnalyzedAt());
        response.setCreateTime(analysis.getCreateTime());
        response.setUpdateTime(analysis.getUpdateTime());
        return response;
    }
}
