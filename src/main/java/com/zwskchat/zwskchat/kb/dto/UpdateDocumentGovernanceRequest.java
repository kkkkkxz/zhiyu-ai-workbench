package com.zwskchat.zwskchat.kb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档知识治理元数据录入/更新请求（对应工程计划书“统一知识对象模型”）。
 * 所有字段可选，仅对非空字段做部分更新（PATCH 语义）。
 */
@Data
public class UpdateDocumentGovernanceRequest {

    /** 资产类型。 */
    @Pattern(regexp = "COMPANY_PROFILE|CASE|SOLUTION|EXTERNAL|GENERAL",
            message = "assetType 取值必须为 COMPANY_PROFILE/CASE/SOLUTION/EXTERNAL/GENERAL")
    private String assetType;

    @Size(max = 32)
    private String docVersion;

    @Size(max = 64)
    private String industry;

    @Size(max = 64)
    private String scenario;

    /** 来源权威等级 1~5（5 最权威）。 */
    @Min(value = 1, message = "authorityLevel 取值范围 1~5")
    @Max(value = 5, message = "authorityLevel 取值范围 1~5")
    private Integer authorityLevel;

    @Pattern(regexp = "PUBLIC|INTERNAL|CONFIDENTIAL|SECRET",
            message = "confidentialityLevel 取值必须为 PUBLIC/INTERNAL/CONFIDENTIAL/SECRET")
    private String confidentialityLevel;

    @Pattern(regexp = "PUBLIC_CITABLE|INTERNAL_ONLY|RESTRICTED",
            message = "accessScope 取值必须为 PUBLIC_CITABLE/INTERNAL_ONLY/RESTRICTED")
    private String accessScope;

    /** 内容生效/更新时间。 */
    private LocalDateTime freshnessDate;

    @Size(max = 512)
    private String citationText;

    /**
     * 版本继承关系：本文档所取代的旧文档 ID（对应工程计划书“保留 supersedes 关系，确保历史答案可追踪”）。
     * 仅在做版本替换时填写，指向被替代的历史版本文档 ID。
     */
    @Min(value = 1, message = "supersedesDocId 必须为正整数")
    private Long supersedesDocId;
}
