package com.zwskchat.zwskchat.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_document")
public class KnowledgeDocument {
    private Long id;
    private Long tenantId;
    private Long kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String fileHash;
    private String fileUrl;
    /** PENDING / PARSING / SUCCESS / FAILED */
    private String parseStatus;
    private String parseError;
    private Integer chunkCount;
    private Long uploaderId;

    /** 资产类型：COMPANY_PROFILE（公司简介）/ CASE（案例）/ SOLUTION（方案）/ EXTERNAL（外部权威）/ GENERAL。 */
    private String assetType;
    /** 文档版本号。 */
    private String docVersion;
    /** 行业标签。 */
    private String industry;
    /** 场景标签。 */
    private String scenario;
    /** 来源权威等级 1~5（5 最权威）。 */
    private Integer authorityLevel;
    /** 密级：PUBLIC / INTERNAL / CONFIDENTIAL / SECRET。 */
    private String confidentialityLevel;
    /** 可见/可引用范围：PUBLIC_CITABLE / INTERNAL_ONLY / RESTRICTED。 */
    private String accessScope;
    /** 内容生效/更新时间（时效性基准）。 */
    private LocalDateTime freshnessDate;
    /** 引用话术（对外可引用文本）。 */
    private String citationText;
    /**
     * 版本继承关系：本文档所取代的旧文档 ID（对应工程计划书“保留 effective_date 与 supersedes 关系”）。
     * 新版入库时指向被替代的历史版本，从而保证历史答案可追踪、可回溯。null 表示无前序版本。
     */
    private Long supersedesDocId;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
