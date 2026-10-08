-- =====================================================================
-- V1.3 · 语音关键词记录
-- ---------------------------------------------------------------------
-- 用于记录语音球界面中「人类说的话」提取出的关键词。
-- 关键词按 tenant_id + user_id + keyword 去重，重复命中时增加 hit_count，
-- 并刷新 source_text / update_time，便于前端下滑框查看最近关注点。
-- =====================================================================
USE `zwskchat`;

CREATE TABLE IF NOT EXISTS `voice_keyword` (
    `id`          BIGINT       NOT NULL,
    `tenant_id`   BIGINT       NOT NULL,
    `user_id`     BIGINT       NOT NULL,
    `session_id`  BIGINT                DEFAULT NULL,
    `turn_id`     VARCHAR(128)          DEFAULT NULL,
    `keyword`     VARCHAR(64)  NOT NULL,
    `source_type` VARCHAR(16)  NOT NULL DEFAULT 'AUTO',
    `source_text` VARCHAR(1000)         DEFAULT NULL,
    `hit_count`   INT          NOT NULL DEFAULT 1,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_keyword` (`tenant_id`, `user_id`, `keyword`, `deleted`),
    KEY `idx_user_update` (`tenant_id`, `user_id`, `update_time`),
    KEY `idx_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
