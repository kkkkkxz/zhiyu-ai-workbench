-- =====================================================================
-- 展望数科 · 知语 AI 企业语音 RAG 助手  V1.0 初始化脚本
-- 编码：utf8mb4 / 引擎：InnoDB
-- 设计原则：tenant_id 多租户、逻辑删除、四审计字段、关键索引、最小权限
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `zwskchat`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `zwskchat`;

-- ----------------------- 租户 -----------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `code`         VARCHAR(64)  NOT NULL COMMENT '租户编码',
    `name`         VARCHAR(128) NOT NULL COMMENT '租户名称',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT                DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ----------------------- 部门 -----------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
    `id`           BIGINT       NOT NULL,
    `tenant_id`    BIGINT       NOT NULL,
    `parent_id`    BIGINT       NOT NULL DEFAULT 0,
    `name`         VARCHAR(128) NOT NULL,
    `sort`         INT          NOT NULL DEFAULT 0,
    `status`       TINYINT      NOT NULL DEFAULT 1,
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT                DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门表';

-- ----------------------- 用户 -----------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`           BIGINT       NOT NULL,
    `tenant_id`    BIGINT       NOT NULL,
    `username`     VARCHAR(64)  NOT NULL,
    `password`     VARCHAR(255) NOT NULL COMMENT 'BCrypt',
    `nickname`     VARCHAR(64)           DEFAULT NULL,
    `phone`        VARCHAR(64)           DEFAULT NULL COMMENT '加密存储',
    `email`        VARCHAR(128)          DEFAULT NULL,
    `dept_id`      BIGINT                DEFAULT NULL,
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用 2锁定',
    `last_login_time` DATETIME            DEFAULT NULL,
    `last_login_ip`   VARCHAR(64)         DEFAULT NULL,
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT                DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`, `deleted`),
    KEY `idx_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------- 角色 / 权限 -----------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id`          BIGINT       NOT NULL,
    `tenant_id`   BIGINT       NOT NULL,
    `role_code`   VARCHAR(64)  NOT NULL,
    `role_name`   VARCHAR(64)  NOT NULL,
    `description` VARCHAR(255)          DEFAULT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `create_by`   BIGINT                DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT                DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_role_code` (`tenant_id`, `role_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
    `id`          BIGINT       NOT NULL,
    `parent_id`   BIGINT       NOT NULL DEFAULT 0,
    `perm_code`   VARCHAR(128) NOT NULL COMMENT '权限标识 如 kb:doc:upload',
    `perm_name`   VARCHAR(128) NOT NULL,
    `perm_type`   VARCHAR(16)  NOT NULL COMMENT 'menu/api/data',
    `path`        VARCHAR(255)          DEFAULT NULL,
    `method`      VARCHAR(16)           DEFAULT NULL,
    `sort`        INT          NOT NULL DEFAULT 0,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表（系统级，无 tenant）';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id`        BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `user_id`   BIGINT NOT NULL,
    `role_id`   BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
    `id`            BIGINT NOT NULL,
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

-- ----------------------- 知识库 -----------------------
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
    `id`          BIGINT       NOT NULL,
    `tenant_id`   BIGINT       NOT NULL,
    `name`        VARCHAR(128) NOT NULL,
    `description` TEXT,
    `owner_id`    BIGINT       NOT NULL,
    `visibility`  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC/DEPT/ROLE/USER/PRIVATE',
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `create_by`   BIGINT                DEFAULT NULL,
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   BIGINT                DEFAULT NULL,
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_owner` (`tenant_id`, `owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库';

DROP TABLE IF EXISTS `knowledge_base_acl`;
CREATE TABLE `knowledge_base_acl` (
    `id`           BIGINT      NOT NULL,
    `tenant_id`    BIGINT      NOT NULL,
    `kb_id`        BIGINT      NOT NULL,
    `subject_type` VARCHAR(16) NOT NULL COMMENT 'USER/ROLE/DEPT',
    `subject_id`   BIGINT      NOT NULL,
    `permission`   VARCHAR(16) NOT NULL DEFAULT 'READ' COMMENT 'READ/WRITE/ADMIN',
    `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_subject` (`kb_id`, `subject_type`, `subject_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库访问控制';

DROP TABLE IF EXISTS `knowledge_document`;
CREATE TABLE `knowledge_document` (
    `id`           BIGINT       NOT NULL,
    `tenant_id`    BIGINT       NOT NULL,
    `kb_id`        BIGINT       NOT NULL,
    `file_name`    VARCHAR(255) NOT NULL,
    `file_type`    VARCHAR(32)  NOT NULL,
    `file_size`    BIGINT       NOT NULL DEFAULT 0,
    `file_hash`    VARCHAR(128)          DEFAULT NULL COMMENT 'SHA-256，用于去重',
    `file_url`     VARCHAR(500) NOT NULL,
    `parse_status` VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/SUCCESS/FAILED',
    `parse_error`  TEXT,
    `chunk_count`  INT          NOT NULL DEFAULT 0,
    `uploader_id`  BIGINT       NOT NULL,
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`    BIGINT                DEFAULT NULL,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_kb_status` (`kb_id`, `parse_status`),
    KEY `idx_tenant_create` (`tenant_id`, `create_time`),
    KEY `idx_hash` (`file_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档';

DROP TABLE IF EXISTS `knowledge_chunk`;
CREATE TABLE `knowledge_chunk` (
    `id`               BIGINT       NOT NULL,
    `tenant_id`        BIGINT       NOT NULL,
    `kb_id`            BIGINT       NOT NULL,
    `document_id`      BIGINT       NOT NULL,
    `title`            VARCHAR(255)          DEFAULT NULL,
    `content`          MEDIUMTEXT   NOT NULL,
    `chunk_index`      INT          NOT NULL,
    `token_count`      INT          NOT NULL DEFAULT 0,
    `page_number`      INT                   DEFAULT NULL,
    `vector_id`        VARCHAR(128)          DEFAULT NULL,
    `permission_scope` VARCHAR(255)          DEFAULT NULL COMMENT '继承自文档的权限标签',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_doc_idx` (`document_id`, `chunk_index`),
    KEY `idx_kb_vector` (`kb_id`, `vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档切片';

-- ----------------------- 会话 -----------------------
DROP TABLE IF EXISTS `ai_session`;
CREATE TABLE `ai_session` (
    `id`           BIGINT       NOT NULL,
    `tenant_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `title`        VARCHAR(255)          DEFAULT NULL,
    `session_type` VARCHAR(16)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/VOICE',
    `kb_ids`       VARCHAR(512)          DEFAULT NULL COMMENT '关联知识库 ID 列表 逗号分隔',
    `status`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_update` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
    `id`         BIGINT      NOT NULL,
    `tenant_id`  BIGINT      NOT NULL,
    `session_id` BIGINT      NOT NULL,
    `user_id`    BIGINT      NOT NULL,
    `role`       VARCHAR(16) NOT NULL COMMENT 'user/assistant/system',
    `content`    MEDIUMTEXT  NOT NULL,
    `audio_url`  VARCHAR(500)         DEFAULT NULL,
    `latency_ms` INT                  DEFAULT NULL,
    `token_in`   INT                  DEFAULT NULL,
    `token_out`  INT                  DEFAULT NULL,
    `feedback`   TINYINT     NOT NULL DEFAULT 0 COMMENT '0未评 1赞 2踩',
    `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session_time` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

DROP TABLE IF EXISTS `ai_message_reference`;
CREATE TABLE `ai_message_reference` (
    `id`               BIGINT      NOT NULL,
    `tenant_id`        BIGINT      NOT NULL,
    `message_id`       BIGINT      NOT NULL,
    `document_id`      BIGINT      NOT NULL,
    `chunk_id`         BIGINT      NOT NULL,
    `score`            DECIMAL(10,6) NOT NULL DEFAULT 0,
    `content_snapshot` MEDIUMTEXT,
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息引用来源';

-- ----------------------- 审计 -----------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
    `id`             BIGINT       NOT NULL,
    `tenant_id`      BIGINT                DEFAULT NULL,
    `operator_id`    BIGINT                DEFAULT NULL,
    `operator_name`  VARCHAR(64)           DEFAULT NULL,
    `operation_type` VARCHAR(64)  NOT NULL,
    `target_type`    VARCHAR(64)           DEFAULT NULL,
    `target_id`      VARCHAR(64)           DEFAULT NULL,
    `result`         VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
    `ip`             VARCHAR(64)           DEFAULT NULL,
    `user_agent`     VARCHAR(500)          DEFAULT NULL,
    `request_uri`    VARCHAR(500)          DEFAULT NULL,
    `request_method` VARCHAR(16)           DEFAULT NULL,
    `cost_ms`        INT                   DEFAULT NULL,
    `detail`         TEXT,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_operator_time` (`operator_id`, `create_time`),
    KEY `idx_optype_time` (`operation_type`, `create_time`),
    KEY `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ----------------------- 初始化数据：默认租户 / 超管 / 角色 -----------------------
INSERT INTO `sys_tenant` (`id`, `code`, `name`) VALUES (1, 'zwsk', '展望数科');

-- 初始密码由部署环境注入，不在源码中提供默认凭据。
INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `status`)
VALUES
(1, 1, 'admin', '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '超级管理员', 1);

INSERT INTO `sys_role`(`id`, `tenant_id`, `role_code`, `role_name`) VALUES
(1, 1, 'SUPER_ADMIN', '超级管理员'),
(2, 1, 'TENANT_ADMIN', '企业管理员'),
(3, 1, 'KB_ADMIN', '知识库管理员'),
(4, 1, 'STAFF', '普通员工'),
(5, 1, 'AUDITOR', '审计人员');

INSERT INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`) VALUES (1, 1, 1, 1);

SET FOREIGN_KEY_CHECKS = 1;
