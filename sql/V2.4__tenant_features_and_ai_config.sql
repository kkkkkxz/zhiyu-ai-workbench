-- =====================================================================
-- V2.4 · 租户功能开关 + 租户级 AI Key 实时配置
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

CREATE TABLE IF NOT EXISTS `tenant_feature_config` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`            BIGINT       NOT NULL,
    `feature_code`         VARCHAR(80)  NOT NULL COMMENT '功能编码，与前端菜单 featureCode 对齐',
    `feature_name`         VARCHAR(100) NOT NULL,
    `category`             VARCHAR(50)  NOT NULL,
    `description`          VARCHAR(500)          DEFAULT NULL,
    `source_template_code` VARCHAR(50)           DEFAULT NULL COMMENT '最近一次由哪个模板启用',
    `enabled`              TINYINT      NOT NULL DEFAULT 0,
    `config_json`          JSON                  DEFAULT NULL,
    `sort`                 INT          NOT NULL DEFAULT 0,
    `create_by`            BIGINT                DEFAULT NULL,
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`            BIGINT                DEFAULT NULL,
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_feature` (`tenant_id`, `feature_code`, `deleted`),
    INDEX `idx_tenant_enabled` (`tenant_id`, `enabled`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户功能开关';

CREATE TABLE IF NOT EXISTS `tenant_ai_config` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `provider`        VARCHAR(50)  NOT NULL DEFAULT 'dashscope-sdk' COMMENT 'dashscope-sdk/openai-compat',
    `base_url`        VARCHAR(300)          DEFAULT NULL,
    `api_key_enc`     VARCHAR(1000)         DEFAULT NULL COMMENT '加密后的模型 API Key',
    `api_key_mask`    VARCHAR(80)           DEFAULT NULL COMMENT '脱敏展示值',
    `chat_model`      VARCHAR(100) NOT NULL DEFAULT 'qwen-plus',
    `embedding_model` VARCHAR(100) NOT NULL DEFAULT 'text-embedding-v3',
    `vision_model`    VARCHAR(100) NOT NULL DEFAULT 'qwen-vl-plus',
    `temperature`     DECIMAL(4,2) NOT NULL DEFAULT 0.30,
    `enabled`         TINYINT      NOT NULL DEFAULT 0,
    `remark`          VARCHAR(300)          DEFAULT NULL,
    `create_by`       BIGINT                DEFAULT NULL,
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`       BIGINT                DEFAULT NULL,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_ai_config` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 AI 模型配置';

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`)
VALUES
    (6101, 0,    'tenant-console:menu',       '公司控制台',       'menu', '/tenant-admin',                                  NULL,   97, 1),
    (6102, 6101, 'tenant-feature:list',       '查看公司功能',     'api',  '/tenant-console/features',                      'GET',  98, 1),
    (6103, 6101, 'tenant-feature:manage',     '管理公司功能',     'api',  '/tenant-console/tenants/*/features/*',          'PUT',  99, 1),
    (6104, 6101, 'tenant-ai-config:read',     '查看公司 AI 配置', 'api',  '/tenant-console/tenants/*/ai-config',           'GET', 100, 1),
    (6105, 6101, 'tenant-ai-config:replace',  '替换公司 AI Key',  'api',  '/tenant-console/tenants/*/ai-config',           'PUT', 101, 1);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5200000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id BETWEEN 6101 AND 6105;

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (5300000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id BETWEEN 6101 AND 6105;

INSERT IGNORE INTO `tenant_feature_config`
(`tenant_id`, `feature_code`, `feature_name`, `category`, `description`, `enabled`, `config_json`, `sort`)
SELECT t.id, x.feature_code, x.feature_name, x.category, x.description, x.enabled, JSON_OBJECT(), x.sort
FROM `sys_tenant` t
JOIN (
    SELECT 'workbench' AS feature_code, '工作台' AS feature_name, '基础' AS category, '企业待办、快捷入口与核心概览' AS description, 1 AS enabled, 10 AS sort
    UNION ALL SELECT 'template_center', '模板中心', '基础', '按行业/业务模板开通功能', 1, 20
    UNION ALL SELECT 'ai_chat', 'AI 对话', 'AI 功能', '通用问答、RAG 知识问答与业务助手入口', 1, 30
    UNION ALL SELECT 'ai_actions', 'AI 动作中心', 'AI 功能', '查看 AI 嵌入业务流程后的执行记录', 1, 40
    UNION ALL SELECT 'knowledge_base', '知识库', 'AI 功能', '文档上传、解析、检索与权限控制', 1, 50
    UNION ALL SELECT 'voice_ai', '语音助手', 'AI 功能', '语音问答、ASR/TTS 与实时对话', 0, 60
    UNION ALL SELECT 'voice_diagnostic', '语音诊断', 'AI 功能', '麦克风、设备和语音链路诊断', 0, 70
    UNION ALL SELECT 'crm_customers', '客户管理', 'ERP 业务', '客户档案、联系人与跟进', 0, 110
    UNION ALL SELECT 'contracts', '合同管理', 'ERP 业务', '合同台账、附件、里程碑与 AI 风险识别', 0, 120
    UNION ALL SELECT 'work_orders', '工单管理', 'ERP 业务', '客户报修、内部工单、附件判断与 AI 建议', 0, 130
    UNION ALL SELECT 'dispatch', '工作派发', 'ERP 业务', '任务派发、执行反馈与进度跟踪', 0, 140
    UNION ALL SELECT 'schedule', '日程管理', 'ERP 业务', '会议、提醒、跟进和任务日历', 0, 150
    UNION ALL SELECT 'workflow', '审批管理', 'ERP 业务', '合同、费用、任务等审批流转', 0, 160
    UNION ALL SELECT 'notifications', '通知中心', 'ERP 业务', '待办、提醒与系统通知', 0, 170
    UNION ALL SELECT 'tenant_dashboard', '数据看板', '经营分析', '当前公司业务数据与 AI 使用情况', 0, 210
    UNION ALL SELECT 'departments', '部门管理', '组织管理', '组织架构、上下级和部门维护', 1, 310
    UNION ALL SELECT 'tenant_console', '公司控制台', '组织管理', '租户模块开关、AI Key 与公司配置', 1, 320
) x
WHERE t.deleted = 0;

UPDATE `tenant_feature_config` tf
JOIN `tpl_install_record` tr ON tr.tenant_id = tf.tenant_id
SET tf.enabled = 1,
    tf.source_template_code = tr.package_code
WHERE tr.deleted = 0
  AND tr.status = 'installed'
  AND tr.package_code IN ('general-business', 'project-delivery', 'after-sales-service')
  AND tf.feature_code IN (
      'crm_customers', 'contracts', 'work_orders', 'dispatch', 'schedule',
      'workflow', 'notifications', 'tenant_dashboard', 'ai_actions', 'knowledge_base'
  );
