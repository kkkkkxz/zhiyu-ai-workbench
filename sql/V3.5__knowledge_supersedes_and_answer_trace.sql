-- =====================================================================
-- V3.5 知识版本继承 + 回答可追溯字段
--   1. knowledge_document.supersedes_doc_id：版本继承关系，新版指向被替代的旧版本，
--      对应《工程计划书》“保留 effective_date 与 supersedes 关系，确保历史答案可追踪”。
--   2. ai_message 增加回答回溯字段：model_id / prompt_hash / policy_version /
--      confidence_score / confidence_decision，对应《工程计划书》“回溯机制方面，建议每次
--      回答都保存 model_id、prompt_hash、policy_version 和 confidence”。
--   全部为可空列，存量数据不受影响。
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等迁移。
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. knowledge_document.supersedes_doc_id ========================
DROP PROCEDURE IF EXISTS _v35_add_supersedes;
DELIMITER //
CREATE PROCEDURE _v35_add_supersedes()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='knowledge_document' AND COLUMN_NAME='supersedes_doc_id') THEN
        ALTER TABLE `knowledge_document`
            ADD COLUMN `supersedes_doc_id` BIGINT DEFAULT NULL COMMENT '本文档所取代的旧版本文档 ID（版本继承链，null=无前序）' AFTER `citation_text`,
            ADD INDEX `idx_doc_supersedes` (`supersedes_doc_id`);
    END IF;
END //
DELIMITER ;
CALL _v35_add_supersedes();
DROP PROCEDURE IF EXISTS _v35_add_supersedes;

-- ======================== 2. ai_message 回答可追溯字段 ========================
DROP PROCEDURE IF EXISTS _v35_add_answer_trace;
DELIMITER //
CREATE PROCEDURE _v35_add_answer_trace()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='ai_message' AND COLUMN_NAME='model_id') THEN
        ALTER TABLE `ai_message`
            ADD COLUMN `model_id`             VARCHAR(128)  DEFAULT NULL COMMENT '生成该回答的模型标识（如 qwen-plus / gpt-4o-mini）' AFTER `token_out`,
            ADD COLUMN `prompt_hash`          VARCHAR(64)   DEFAULT NULL COMMENT 'system+context 提示词的 SHA-256（便于回归与回溯）' AFTER `model_id`,
            ADD COLUMN `policy_version`       VARCHAR(32)   DEFAULT NULL COMMENT '生成时生效的治理/安全策略版本' AFTER `prompt_hash`,
            ADD COLUMN `confidence_score`     DECIMAL(6,4)  DEFAULT NULL COMMENT '组合可信度评分 [0,1]' AFTER `policy_version`,
            ADD COLUMN `confidence_decision`  VARCHAR(16)   DEFAULT NULL COMMENT '可信度三档决策 ANSWER/CAUTIOUS/REFUSE' AFTER `confidence_score`;
    END IF;
END //
DELIMITER ;
CALL _v35_add_answer_trace();
DROP PROCEDURE IF EXISTS _v35_add_answer_trace;
