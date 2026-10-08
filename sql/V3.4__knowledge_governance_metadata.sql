-- =====================================================================
-- V3.4 知识治理元数据：统一知识对象模型（公司简介 / 案例 / 110 方案 / 外部权威）
--   为 knowledge_document、knowledge_chunk 增加治理字段：
--   资产类型 / 版本 / 行业 / 场景 / 权威等级 / 密级 / 可见范围 / 时效时间 / 引用话术。
--   全部为可空列，存量数据不受影响；权威等级与时效时间用于可信度评分真实信号。
-- 对应《企业第二大脑智能问答助手工程计划书》“数据资产与知识治理”章节。
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等迁移。
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. knowledge_document 治理字段 ========================
DROP PROCEDURE IF EXISTS _v34_add_document_cols;
DELIMITER //
CREATE PROCEDURE _v34_add_document_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='knowledge_document' AND COLUMN_NAME='asset_type') THEN
        ALTER TABLE `knowledge_document`
            ADD COLUMN `asset_type`            VARCHAR(32)   DEFAULT NULL COMMENT '资产类型 COMPANY_PROFILE/CASE/SOLUTION/EXTERNAL/GENERAL' AFTER `uploader_id`,
            ADD COLUMN `doc_version`           VARCHAR(32)   DEFAULT NULL COMMENT '文档版本号' AFTER `asset_type`,
            ADD COLUMN `industry`              VARCHAR(64)   DEFAULT NULL COMMENT '行业标签' AFTER `doc_version`,
            ADD COLUMN `scenario`              VARCHAR(64)   DEFAULT NULL COMMENT '场景标签' AFTER `industry`,
            ADD COLUMN `authority_level`       TINYINT       DEFAULT NULL COMMENT '来源权威等级 1~5（5 最权威）' AFTER `scenario`,
            ADD COLUMN `confidentiality_level` VARCHAR(16)   DEFAULT NULL COMMENT '密级 PUBLIC/INTERNAL/CONFIDENTIAL/SECRET' AFTER `authority_level`,
            ADD COLUMN `access_scope`          VARCHAR(32)   DEFAULT NULL COMMENT '可见范围 PUBLIC_CITABLE/INTERNAL_ONLY/RESTRICTED' AFTER `confidentiality_level`,
            ADD COLUMN `freshness_date`        DATETIME      DEFAULT NULL COMMENT '内容生效/更新时间' AFTER `access_scope`,
            ADD COLUMN `citation_text`         VARCHAR(512)  DEFAULT NULL COMMENT '对外可引用话术' AFTER `freshness_date`;
    END IF;
END //
DELIMITER ;
CALL _v34_add_document_cols();
DROP PROCEDURE IF EXISTS _v34_add_document_cols;

-- ======================== 2. knowledge_chunk 治理字段（从文档冗余，便于过滤与评分） ========================
DROP PROCEDURE IF EXISTS _v34_add_chunk_cols;
DELIMITER //
CREATE PROCEDURE _v34_add_chunk_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='knowledge_chunk' AND COLUMN_NAME='asset_type') THEN
        ALTER TABLE `knowledge_chunk`
            ADD COLUMN `asset_type`            VARCHAR(32)   DEFAULT NULL COMMENT '资产类型（从文档冗余）' AFTER `permission_scope`,
            ADD COLUMN `authority_level`       TINYINT       DEFAULT NULL COMMENT '来源权威等级 1~5' AFTER `asset_type`,
            ADD COLUMN `confidentiality_level` VARCHAR(16)   DEFAULT NULL COMMENT '密级' AFTER `authority_level`,
            ADD COLUMN `freshness_date`        DATETIME      DEFAULT NULL COMMENT '内容生效/更新时间' AFTER `confidentiality_level`;
    END IF;
END //
DELIMITER ;
CALL _v34_add_chunk_cols();
DROP PROCEDURE IF EXISTS _v34_add_chunk_cols;

-- ======================== 3. 检索过滤辅助索引 ========================
DROP PROCEDURE IF EXISTS _v34_add_chunk_index;
DELIMITER //
CREATE PROCEDURE _v34_add_chunk_index()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='knowledge_chunk' AND INDEX_NAME='idx_chunk_asset_type') THEN
        ALTER TABLE `knowledge_chunk` ADD INDEX `idx_chunk_asset_type` (`asset_type`);
    END IF;
END //
DELIMITER ;
CALL _v34_add_chunk_index();
DROP PROCEDURE IF EXISTS _v34_add_chunk_index;
