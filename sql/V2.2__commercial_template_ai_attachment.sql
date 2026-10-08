-- =====================================================================
-- V2.2 · 商用化底座
-- 包含：通用附件、模板中心、AI 动作审计、编号规则
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. 通用附件中心 ========================

CREATE TABLE IF NOT EXISTS `biz_attachment` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `biz_type`      VARCHAR(50)  NOT NULL COMMENT '业务类型 contract/work_order/customer/dispatch/schedule/workflow',
    `biz_id`        BIGINT       NOT NULL COMMENT '业务记录 ID',
    `category`      VARCHAR(50)  NOT NULL DEFAULT 'general' COMMENT '附件分类 original/signed/evidence/report/general',
    `file_name`     VARCHAR(200) NOT NULL,
    `file_key`      VARCHAR(500) NOT NULL COMMENT '对象存储 key',
    `file_type`     VARCHAR(50)           DEFAULT NULL,
    `content_type`  VARCHAR(100)          DEFAULT NULL,
    `file_size`     BIGINT       NOT NULL DEFAULT 0,
    `file_hash`     VARCHAR(64)  NOT NULL COMMENT 'SHA-256',
    `version_no`    INT          NOT NULL DEFAULT 1,
    `source`        VARCHAR(30)  NOT NULL DEFAULT 'manual' COMMENT 'manual/ai/import/api',
    `visibility`    VARCHAR(20)  NOT NULL DEFAULT 'internal' COMMENT 'internal/private/public',
    `uploader_id`   BIGINT       NOT NULL,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_biz` (`tenant_id`, `biz_type`, `biz_id`, `deleted`),
    INDEX `idx_uploader` (`tenant_id`, `uploader_id`, `create_time`),
    INDEX `idx_hash` (`tenant_id`, `file_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用业务附件';

-- ======================== 2. 模板中心 ========================

CREATE TABLE IF NOT EXISTS `tpl_package` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `code`          VARCHAR(50)  NOT NULL COMMENT '模板编码',
    `name`          VARCHAR(100) NOT NULL,
    `category`      VARCHAR(50)  NOT NULL DEFAULT 'business' COMMENT 'business/project/service/office',
    `description`   VARCHAR(500)          DEFAULT NULL,
    `scenario`      VARCHAR(200)          DEFAULT NULL COMMENT '适用场景',
    `modules_json`  JSON                  DEFAULT NULL COMMENT '模块清单',
    `ai_agents_json` JSON                 DEFAULT NULL COMMENT 'AI Agent 清单',
    `status`        TINYINT      NOT NULL DEFAULT 1,
    `sort`          INT          NOT NULL DEFAULT 0,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code` (`code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板包';

CREATE TABLE IF NOT EXISTS `tpl_install_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `package_code`  VARCHAR(50)  NOT NULL,
    `package_name`  VARCHAR(100) NOT NULL,
    `version`       VARCHAR(30)  NOT NULL DEFAULT '1.0.0',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'installed' COMMENT 'installed/disabled/upgrading',
    `config_json`   JSON                  DEFAULT NULL,
    `installed_by`  BIGINT       NOT NULL,
    `installed_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_package` (`tenant_id`, `package_code`, `deleted`),
    INDEX `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户模板安装记录';

INSERT IGNORE INTO `tpl_package`
(`id`, `code`, `name`, `category`, `description`, `scenario`, `modules_json`, `ai_agents_json`, `sort`)
VALUES
(1, 'general-business', '通用经营模板', 'business',
 '适合大多数中小企业的客户、合同、工单、审批、日程和经营看板。',
 '客户跟进、合同管理、售后工单、日常审批',
 JSON_ARRAY('客户管理', '合同管理', '工单管理', '工作派发', '日程提醒', '审批管理', '经营看板'),
 JSON_ARRAY('销售助理', '合同助理', '客服助理', '管理助理'),
 10),
(2, 'project-delivery', '项目交付模板', 'project',
 '适合软件开发、工程实施、咨询服务、活动执行等项目型业务。',
 '需求拆解、项目计划、任务派发、交付物、验收、周报',
 JSON_ARRAY('客户管理', '合同管理', '项目管理', '任务管理', '交付物', '验收', '周报', '回款计划'),
 JSON_ARRAY('项目助理', '任务拆解助理', '周报助理', '风险预警助理'),
 20),
(3, 'after-sales-service', '售后服务模板', 'service',
 '适合客服、维修、运维、设备服务、物业服务等售后场景。',
 '客户报修、工单分类、SLA、派单、处理、回访、知识沉淀',
 JSON_ARRAY('客户管理', '服务协议', '工单管理', 'SLA', '工作派发', '回访', '知识库', '服务看板'),
 JSON_ARRAY('客服助理', '派单助理', '知识推荐助理', '回访助理'),
 30);

-- ======================== 3. AI 动作审计 ========================

CREATE TABLE IF NOT EXISTS `ai_action_execution_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `user_id`         BIGINT       NOT NULL,
    `action_code`     VARCHAR(80)  NOT NULL,
    `action_name`     VARCHAR(120)          DEFAULT NULL,
    `biz_type`        VARCHAR(50)           DEFAULT NULL,
    `biz_id`          BIGINT                DEFAULT NULL,
    `phase`           VARCHAR(20)  NOT NULL COMMENT 'PREVIEW/EXECUTE',
    `risk_level`      VARCHAR(20)  NOT NULL DEFAULT 'LOW',
    `user_input`      VARCHAR(1000)         DEFAULT NULL,
    `preview_json`    JSON                  DEFAULT NULL,
    `result_json`     JSON                  DEFAULT NULL,
    `status`          VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/FAILED/CANCELED',
    `error_message`   VARCHAR(1000)         DEFAULT NULL,
    `trace_id`        VARCHAR(64)           DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_user_time` (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_action_time` (`tenant_id`, `action_code`, `create_time`),
    INDEX `idx_biz` (`tenant_id`, `biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 业务动作执行审计';

-- ======================== 4. 编号规则 ========================

CREATE TABLE IF NOT EXISTS `sys_number_rule` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `code`          VARCHAR(50)  NOT NULL COMMENT '规则编码 contract/work_order/project',
    `name`          VARCHAR(100) NOT NULL,
    `prefix`        VARCHAR(30)  NOT NULL,
    `date_pattern`  VARCHAR(20)  NOT NULL DEFAULT 'yyyyMMdd',
    `seq_length`    INT          NOT NULL DEFAULT 5,
    `enabled`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_code` (`tenant_id`, `code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编号规则';

CREATE TABLE IF NOT EXISTS `sys_number_sequence` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT      NOT NULL,
    `code`          VARCHAR(50) NOT NULL,
    `date_key`      VARCHAR(20) NOT NULL,
    `current_value` BIGINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_code_date` (`tenant_id`, `code`, `date_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务编号序列';

-- ======================== 5. 权限 ========================

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`)
VALUES
    (5801, 0,    'attachment:menu',    '附件中心',       'menu', '/attachments',             NULL,   88, 1),
    (5802, 5801, 'attachment:list',    '查看附件',       'api',  '/attachments/**',          'GET',  89, 1),
    (5803, 5801, 'attachment:upload',  '上传附件',       'api',  '/attachments/**',          'POST', 90, 1),
    (5804, 5801, 'attachment:delete',  '删除附件',       'api',  '/attachments/**',          'DELETE', 91, 1),
    (5901, 0,    'template:menu',      '模板中心',       'menu', '/templates',               NULL,   92, 1),
    (5902, 5901, 'template:list',      '查看模板',       'api',  '/templates/**',            'GET',  93, 1),
    (5903, 5901, 'template:install',   '安装模板',       'api',  '/templates/*/install',     'POST', 94, 1),
    (6001, 0,    'ai-action:menu',     'AI 动作中心',    'menu', '/ai-actions',              NULL,   95, 1),
    (6002, 6001, 'ai-action:logs',     '查看 AI 动作',   'api',  '/ai-action/logs',          'GET',  96, 1);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5000000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id >= 5801 AND p.id <= 6002;

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5100000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id >= 5801 AND p.id <= 6002;

