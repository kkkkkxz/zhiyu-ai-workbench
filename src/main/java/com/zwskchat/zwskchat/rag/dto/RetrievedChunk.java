package com.zwskchat.zwskchat.rag.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 检索命中片段。除基础内容外，携带知识治理元数据（来源权威等级、时效时间），
 * 供可信度评分计算真实的 source_authority / freshness 信号。
 */
@Data
@NoArgsConstructor
public class RetrievedChunk {
    private Long chunkId;
    private Long documentId;
    private Long kbId;
    private String title;
    private String content;
    private double score;
    private Integer pageNumber;

    /** 来源权威等级 1~5（5 最权威）；为空表示未标注，评分时回退默认值。 */
    private Integer authorityLevel;
    /** 内容生效/更新时间；为空表示未标注，评分时回退默认值。 */
    private LocalDateTime freshnessDate;
    /** 资产类型：COMPANY_PROFILE / CASE / SOLUTION / EXTERNAL / GENERAL。 */
    private String assetType;
    /** 密级：PUBLIC / INTERNAL / CONFIDENTIAL / SECRET。 */
    private String confidentialityLevel;

    public RetrievedChunk(Long chunkId, Long documentId, Long kbId, String title, String content, double score) {
        this(chunkId, documentId, kbId, title, content, score, null);
    }

    public RetrievedChunk(Long chunkId, Long documentId, Long kbId, String title, String content,
                          double score, Integer pageNumber) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.kbId = kbId;
        this.title = title;
        this.content = content;
        this.score = score;
        this.pageNumber = pageNumber;
    }
}
