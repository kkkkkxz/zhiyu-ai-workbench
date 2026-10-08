package com.zwskchat.zwskchat.kb.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {
    private Long id;
    private Long tenantId;
    private Long kbId;
    private Long documentId;
    private String title;
    private String content;
    private Integer chunkIndex;
    private Integer tokenCount;
    private Integer pageNumber;
    private String vectorId;
    private String permissionScope;
    /** 资产类型：COMPANY_PROFILE / CASE / SOLUTION / EXTERNAL / GENERAL（从文档冗余而来，便于检索过滤与评分）。 */
    private String assetType;
    /** 来源权威等级 1~5（5 最权威）。 */
    private Integer authorityLevel;
    /** 密级：PUBLIC / INTERNAL / CONFIDENTIAL / SECRET。 */
    private String confidentialityLevel;
    /** 内容生效/更新时间。 */
    private LocalDateTime freshnessDate;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
