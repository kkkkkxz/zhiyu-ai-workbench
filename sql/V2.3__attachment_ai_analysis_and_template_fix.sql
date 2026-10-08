-- =====================================================================
-- V2.3 · 附件 AI 解析判断 + 模板中心数据订正
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- 1. 修复曾经因客户端编码导致的模板种子数据问号化问题。
UPDATE `tpl_package`
SET `name` = '通用经营模板',
    `description` = '适合大多数中小企业的客户、合同、工单、审批、日程和经营看板。',
    `scenario` = '客户跟进、合同管理、售后工单、日常审批',
    `modules_json` = JSON_ARRAY('客户管理', '合同管理', '工单管理', '工作派发', '日程提醒', '审批管理', '经营看板'),
    `ai_agents_json` = JSON_ARRAY('销售助理', '合同助理', '客服助理', '管理助理')
WHERE `code` = 'general-business';

UPDATE `tpl_package`
SET `name` = '项目交付模板',
    `description` = '适合软件开发、工程实施、咨询服务、活动执行等项目型业务。',
    `scenario` = '需求拆解、项目计划、任务派发、交付物、验收、周报',
    `modules_json` = JSON_ARRAY('客户管理', '合同管理', '项目管理', '任务管理', '交付物', '验收', '周报', '回款计划'),
    `ai_agents_json` = JSON_ARRAY('项目助理', '任务拆解助理', '周报助理', '风险预警助理')
WHERE `code` = 'project-delivery';

UPDATE `tpl_package`
SET `name` = '售后服务模板',
    `description` = '适合客服、维修、运维、设备服务、物业服务等售后场景。',
    `scenario` = '客户报修、工单分类、SLA、派单、处理、回访、知识沉淀',
    `modules_json` = JSON_ARRAY('客户管理', '服务协议', '工单管理', 'SLA', '工作派发', '回访', '知识库', '服务看板'),
    `ai_agents_json` = JSON_ARRAY('客服助理', '派单助理', '知识推荐助理', '回访助理')
WHERE `code` = 'after-sales-service';

-- 2. 附件 AI 解析结果：保留结构化判断，避免业务文件只成为“网盘附件”。
CREATE TABLE IF NOT EXISTS `biz_attachment_analysis` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`            BIGINT       NOT NULL,
    `attachment_id`        BIGINT       NOT NULL COMMENT '附件 ID',
    `biz_type`             VARCHAR(50)  NOT NULL COMMENT '业务类型',
    `biz_id`               BIGINT       NOT NULL COMMENT '业务记录 ID',
    `file_name`            VARCHAR(200) NOT NULL,
    `file_hash`            VARCHAR(64)           DEFAULT NULL,
    `status`               VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PARSING/SUCCESS/FAILED/SKIPPED',
    `risk_level`           VARCHAR(20)           DEFAULT NULL COMMENT 'LOW/MEDIUM/HIGH',
    `document_type`        VARCHAR(50)           DEFAULT NULL COMMENT '合同/工单凭证/客户资料等',
    `summary`              VARCHAR(1000)         DEFAULT NULL,
    `key_fields_json`      JSON                  DEFAULT NULL COMMENT 'AI 提取的关键字段',
    `missing_fields_json`  JSON                  DEFAULT NULL COMMENT 'AI 判断缺失项',
    `risks_json`           JSON                  DEFAULT NULL COMMENT 'AI 风险点',
    `suggestions_json`     JSON                  DEFAULT NULL COMMENT 'AI 建议',
    `next_actions_json`    JSON                  DEFAULT NULL COMMENT '建议下一步动作',
    `raw_ai_response`      TEXT                  DEFAULT NULL COMMENT '原始 AI 响应，便于排错',
    `parse_error`          VARCHAR(1000)         DEFAULT NULL,
    `parsed_text_chars`    INT          NOT NULL DEFAULT 0,
    `analyzed_by`          BIGINT                DEFAULT NULL,
    `analyzed_at`          DATETIME              DEFAULT NULL,
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_attachment` (`tenant_id`, `attachment_id`, `deleted`),
    INDEX `idx_biz` (`tenant_id`, `biz_type`, `biz_id`, `status`),
    INDEX `idx_status_time` (`tenant_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务附件 AI 解析判断';

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`)
VALUES
    (5805, 5801, 'attachment:analysis:list',  '查看附件 AI 解析', 'api', '/attachments/**/analysis*', 'GET',  92, 1),
    (5806, 5801, 'attachment:analysis:retry', '重跑附件 AI 解析', 'api', '/attachments/**/analysis*', 'POST', 93, 1);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5000000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id IN (5805, 5806);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5100000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id IN (5805, 5806);
