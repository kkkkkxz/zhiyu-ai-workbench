-- =====================================================================
-- V3.0 · AI 全业务版底座扩展
-- 包含：业务时间线、AI 动作定义注册、AI 模型用量统计、数据权限范围、模板版本
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. 业务时间线 ========================

CREATE TABLE IF NOT EXISTS `biz_timeline_event` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `biz_type`      VARCHAR(50)  NOT NULL COMMENT '业务类型 customer/contract/work_order/dispatch/schedule/workflow',
    `biz_id`        BIGINT       NOT NULL COMMENT '业务记录 ID',
    `event_type`    VARCHAR(50)  NOT NULL COMMENT '事件类型 created/updated/status_changed/assigned/commented/attachment/approval/ai_action',
    `title`         VARCHAR(200) NOT NULL COMMENT '事件标题',
    `content`       TEXT                  DEFAULT NULL COMMENT '事件详情（JSON 或文本）',
    `old_value`     VARCHAR(500)          DEFAULT NULL COMMENT '变更前值',
    `new_value`     VARCHAR(500)          DEFAULT NULL COMMENT '变更后值',
    `field_name`    VARCHAR(100)          DEFAULT NULL COMMENT '变更字段名',
    `operator_id`   BIGINT       NOT NULL COMMENT '操作人',
    `operator_name` VARCHAR(64)           DEFAULT NULL COMMENT '操作人姓名（冗余）',
    `source`        VARCHAR(30)  NOT NULL DEFAULT 'user' COMMENT '来源 user/system/ai/api',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_biz` (`tenant_id`, `biz_type`, `biz_id`, `create_time`),
    INDEX `idx_operator` (`tenant_id`, `operator_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务时间线事件';

-- ======================== 2. AI 动作定义注册 ========================

CREATE TABLE IF NOT EXISTS `ai_action_definition` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL DEFAULT 0 COMMENT '0=全局，>0=租户级',
    `action_code`     VARCHAR(80)  NOT NULL COMMENT '动作编码 如 CREATE_CUSTOMER',
    `name`            VARCHAR(120) NOT NULL COMMENT '动作名称',
    `description`     VARCHAR(500)          DEFAULT NULL COMMENT '动作描述（给 AI 看）',
    `biz_type`        VARCHAR(50)           DEFAULT NULL COMMENT '关联业务类型',
    `risk_level`      VARCHAR(20)  NOT NULL DEFAULT 'LOW' COMMENT 'LOW/MEDIUM/HIGH/CRITICAL',
    `permission_code` VARCHAR(128)          DEFAULT NULL COMMENT '所需权限点',
    `param_schema`    JSON         NOT NULL COMMENT '参数 JSON Schema',
    `preview_template` VARCHAR(1000)        DEFAULT NULL COMMENT '预览文本模板',
    `executor_bean`   VARCHAR(100)          DEFAULT NULL COMMENT 'Spring Bean 名称',
    `confirm_required` TINYINT    NOT NULL DEFAULT 1 COMMENT '是否需要用户确认 0=自动 1=需确认',
    `enabled`         TINYINT     NOT NULL DEFAULT 1,
    `sort`            INT         NOT NULL DEFAULT 0,
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_action_code` (`tenant_id`, `action_code`, `deleted`),
    INDEX `idx_biz_type` (`biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 动作定义注册';

-- 内置全局动作种子数据
INSERT IGNORE INTO `ai_action_definition`
(`id`, `tenant_id`, `action_code`, `name`, `description`, `biz_type`, `risk_level`, `permission_code`, `param_schema`, `confirm_required`, `sort`)
VALUES
(1, 0, 'CREATE_CUSTOMER', '创建客户', '根据用户输入创建客户档案', 'customer', 'LOW', 'crm:customer:create',
 '{"type":"object","properties":{"name":{"type":"string","description":"客户名称"},"type":{"type":"string","enum":["enterprise","individual"]},"industry":{"type":"string"},"phone":{"type":"string"},"email":{"type":"string"}}}',
 1, 10),
(2, 0, 'CREATE_CONTRACT', '创建合同', '根据用户输入创建合同', 'contract', 'MEDIUM', 'contract:create',
 '{"type":"object","properties":{"title":{"type":"string","description":"合同标题"},"contractNo":{"type":"string"},"type":{"type":"string","enum":["sales","purchase","service"]},"amount":{"type":"number"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"}}}',
 1, 20),
(3, 0, 'CREATE_WORKORDER', '创建工单', '根据用户输入创建工单', 'work_order', 'LOW', 'workorder:create',
 '{"type":"object","properties":{"title":{"type":"string","description":"工单标题"},"type":{"type":"string","enum":["support","bug","feature","maintenance"]},"priority":{"type":"string","enum":["low","medium","high","urgent"]},"description":{"type":"string"}}}',
 1, 30),
(4, 0, 'DISPATCH', '派发任务', '根据用户输入派发任务给指定人员', 'dispatch', 'LOW', 'dispatch:create',
 '{"type":"object","properties":{"title":{"type":"string","description":"任务标题"},"targetUserName":{"type":"string","description":"指派人姓名"},"description":{"type":"string"},"dueDate":{"type":"string","format":"date-time"}}}',
 1, 40),
(5, 0, 'CREATE_SCHEDULE', '创建日程', '根据用户输入创建日程和提醒', 'schedule', 'LOW', 'schedule:create',
 '{"type":"object","properties":{"title":{"type":"string","description":"日程标题"},"type":{"type":"string","enum":["event","meeting","reminder","follow_up"]},"startTime":{"type":"string","format":"date-time"},"endTime":{"type":"string","format":"date-time"},"location":{"type":"string"}}}',
 1, 50),
(6, 0, 'START_APPROVAL', '发起审批', '根据用户输入发起审批流程', 'workflow', 'MEDIUM', 'workflow:task:action',
 '{"type":"object","properties":{"title":{"type":"string","description":"审批标题"},"bizType":{"type":"string","enum":["contract","expense","leave","other"]},"reason":{"type":"string"}}}',
 1, 60),
(7, 0, 'ADD_FOLLOW_UP', '添加跟进记录', '为客户添加跟进记录', 'customer', 'LOW', 'crm:followup:create',
 '{"type":"object","properties":{"customerName":{"type":"string","description":"客户名称"},"type":{"type":"string","enum":["visit","call","wechat","email"]},"content":{"type":"string","description":"跟进内容"},"nextPlan":{"type":"string"}}}',
 1, 70),
(8, 0, 'QUERY_CUSTOMER', '查询客户', '搜索查询客户信息', 'customer', 'LOW', 'crm:customer:list',
 '{"type":"object","properties":{"keyword":{"type":"string"},"status":{"type":"string","enum":["active","potential","inactive"]}}}',
 0, 80),
(9, 0, 'QUERY_CONTRACT', '查询合同', '搜索查询合同信息', 'contract', 'LOW', 'contract:list',
 '{"type":"object","properties":{"keyword":{"type":"string"},"status":{"type":"string","enum":["draft","active","completed","terminated"]}}}',
 0, 90),
(10, 0, 'QUERY_WORKORDER', '查询工单', '搜索查询工单信息', 'work_order', 'LOW', 'workorder:list',
 '{"type":"object","properties":{"keyword":{"type":"string"},"status":{"type":"string"},"priority":{"type":"string"}}}',
 0, 100),
(11, 0, 'QUERY_SCHEDULE', '查询日程', '查询用户日程安排', 'schedule', 'LOW', 'schedule:list',
 '{"type":"object","properties":{"keyword":{"type":"string"}}}',
 0, 110);

-- ======================== 3. AI 模型用量统计 ========================

CREATE TABLE IF NOT EXISTS `ai_model_usage_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `user_id`        BIGINT       NOT NULL,
    `session_id`     BIGINT                DEFAULT NULL COMMENT '关联会话',
    `provider`       VARCHAR(50)  NOT NULL COMMENT '供应商 dashscope/openai/local',
    `model`          VARCHAR(80)  NOT NULL COMMENT '模型名称',
    `usage_type`     VARCHAR(30)  NOT NULL COMMENT 'chat/embedding/asr/tts/vision/action',
    `prompt_tokens`  INT          NOT NULL DEFAULT 0,
    `completion_tokens` INT       NOT NULL DEFAULT 0,
    `total_tokens`   INT          NOT NULL DEFAULT 0,
    `latency_ms`     INT                   DEFAULT NULL,
    `biz_type`       VARCHAR(50)           DEFAULT NULL COMMENT '关联业务类型',
    `biz_action`     VARCHAR(80)           DEFAULT NULL COMMENT '关联动作编码',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/TIMEOUT',
    `error_msg`      VARCHAR(500)          DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_time` (`tenant_id`, `create_time`),
    INDEX `idx_tenant_user` (`tenant_id`, `user_id`, `create_time`),
    INDEX `idx_model` (`tenant_id`, `model`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型用量统计';

-- ======================== 4. 数据权限范围 ========================

CREATE TABLE IF NOT EXISTS `sys_data_scope` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`     BIGINT       NOT NULL,
    `role_id`       BIGINT       NOT NULL COMMENT '角色 ID',
    `biz_type`      VARCHAR(50)  NOT NULL COMMENT '业务类型 customer/contract/work_order/dispatch/schedule',
    `scope_type`    VARCHAR(30)  NOT NULL DEFAULT 'self' COMMENT 'all/dept_and_children/dept/self/custom',
    `custom_dept_ids` VARCHAR(500)         DEFAULT NULL COMMENT 'scope_type=custom 时的部门 ID 列表（逗号分隔）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_role_biz` (`tenant_id`, `role_id`, `biz_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限范围配置';

-- ======================== 5. 模板版本管理 ========================

CREATE TABLE IF NOT EXISTS `tpl_package_version` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `package_code`  VARCHAR(50)  NOT NULL COMMENT '模板编码',
    `version`       VARCHAR(30)  NOT NULL COMMENT '版本号 如 1.0.0',
    `changelog`     TEXT                  DEFAULT NULL COMMENT '版本变更说明',
    `modules_json`  JSON                  DEFAULT NULL COMMENT '该版本的模块清单',
    `ai_agents_json` JSON                 DEFAULT NULL COMMENT '该版本的 AI Agent 清单',
    `upgrade_sql`   TEXT                  DEFAULT NULL COMMENT '升级 SQL（从上一版本）',
    `rollback_sql`  TEXT                  DEFAULT NULL COMMENT '回滚 SQL',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=发布 0=草稿',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_package_version` (`package_code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板版本';

-- 初始版本种子
INSERT IGNORE INTO `tpl_package_version`(`id`, `package_code`, `version`, `changelog`, `status`)
VALUES
(1, 'general-business', '1.0.0', '初始版本：客户、合同、工单、派发、日程、审批、经营看板', 1),
(2, 'project-delivery', '1.0.0', '初始版本：项目管理、任务拆解、交付物、验收、周报', 1),
(3, 'after-sales-service', '1.0.0', '初始版本：报修、SLA、派单、回访、知识库', 1);

-- ======================== 6. 新增权限 ========================

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`)
VALUES
    (6101, 0,    'timeline:menu',       '业务时间线',     'menu', '/timeline',              NULL,    97, 1),
    (6102, 6101, 'timeline:list',       '查看时间线',     'api',  '/timeline/**',           'GET',   98, 1),
    (6201, 0,    'ai-action-def:menu',  'AI 动作定义',    'menu', '/ai-action/definitions', NULL,    99, 1),
    (6202, 6201, 'ai-action-def:list',  '查看动作定义',   'api',  '/ai-action/definitions', 'GET',  100, 1),
    (6203, 6201, 'ai-action-def:manage','管理动作定义',   'api',  '/ai-action/definitions', 'POST', 101, 1),
    (6301, 0,    'model-usage:menu',    'AI 用量统计',    'menu', '/ai-model-usage',        NULL,   102, 1),
    (6302, 6301, 'model-usage:list',    '查看用量',       'api',  '/ai-model-usage/**',     'GET',  103, 1),
    (6401, 0,    'data-scope:menu',     '数据权限',       'menu', '/data-scope',            NULL,   104, 1),
    (6402, 6401, 'data-scope:manage',   '管理数据权限',   'api',  '/data-scope/**',         'POST', 105, 1);

-- SUPER_ADMIN 获得所有新权限
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (8000000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id >= 6101 AND p.id <= 6402;

-- TENANT_ADMIN 获得时间线 + 用量
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (8100000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id IN (6101, 6102, 6301, 6302);

-- ======================== 验证 ========================
SELECT '--- V3.0 新建表统计 ---' AS info;
SELECT COUNT(*) AS new_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'zwskchat' AND TABLE_NAME IN (
    'biz_timeline_event',
    'ai_action_definition',
    'ai_model_usage_log',
    'sys_data_scope',
    'tpl_package_version'
);
