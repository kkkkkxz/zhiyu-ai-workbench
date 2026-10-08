-- =====================================================================
-- V2.0 · ERP AI 化全量新增表
-- 包含：AI 数据源、CRM、合同、工单、审批流、派发、日程、提醒、通知、
--       SSO、API 开放平台、文档导出任务
-- 幂等：所有 CREATE TABLE 使用 IF NOT EXISTS
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. AI 中枢 ========================

CREATE TABLE IF NOT EXISTS `ai_data_source` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `name`           VARCHAR(100) NOT NULL COMMENT '数据源名称',
    `type`           VARCHAR(20)  NOT NULL DEFAULT 'mysql' COMMENT 'mysql/api/csv',
    `connection_url` VARCHAR(500)          DEFAULT NULL COMMENT '数据库连接（加密存储）',
    `username`       VARCHAR(100)          DEFAULT NULL,
    `password_enc`   VARCHAR(500)          DEFAULT NULL COMMENT 'AES 加密后的密码',
    `schema_json`    JSON                  DEFAULT NULL COMMENT '表结构元数据缓存',
    `status`         TINYINT      NOT NULL DEFAULT 1,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 业务数据源';

CREATE TABLE IF NOT EXISTS `ai_sql_execution_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `user_id`        BIGINT       NOT NULL,
    `session_id`     BIGINT                DEFAULT NULL,
    `question`       TEXT         NOT NULL COMMENT '用户原始问题',
    `generated_sql`  TEXT                  DEFAULT NULL COMMENT 'AI 生成的 SQL',
    `result_rows`    INT                   DEFAULT NULL COMMENT '返回行数',
    `execution_ms`   INT                   DEFAULT NULL COMMENT '执行耗时',
    `status`         VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/SQL_ERROR/TIMEOUT/REJECTED',
    `error_msg`      TEXT                  DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 执行审计日志';

-- ======================== 2. CRM ========================

CREATE TABLE IF NOT EXISTS `biz_customer` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `name`         VARCHAR(200) NOT NULL COMMENT '客户名称',
    `short_name`   VARCHAR(100)          DEFAULT NULL COMMENT '简称',
    `type`         VARCHAR(20)  NOT NULL DEFAULT 'enterprise' COMMENT 'enterprise/individual',
    `industry`     VARCHAR(50)           DEFAULT NULL COMMENT '行业',
    `scale`        VARCHAR(20)           DEFAULT NULL COMMENT '规模 small/medium/large',
    `source`       VARCHAR(50)           DEFAULT NULL COMMENT '来源渠道',
    `owner_id`     BIGINT                DEFAULT NULL COMMENT '负责业务员',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'potential' COMMENT 'potential/active/inactive/lost',
    `phone`        VARCHAR(30)           DEFAULT NULL,
    `email`        VARCHAR(100)          DEFAULT NULL,
    `address`      VARCHAR(300)          DEFAULT NULL,
    `remark`       TEXT                  DEFAULT NULL,
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_status` (`tenant_id`, `status`),
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户档案';

CREATE TABLE IF NOT EXISTS `biz_contact` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `customer_id`  BIGINT       NOT NULL,
    `name`         VARCHAR(50)  NOT NULL COMMENT '联系人姓名',
    `position`     VARCHAR(50)           DEFAULT NULL COMMENT '职位',
    `phone`        VARCHAR(30)           DEFAULT NULL,
    `email`        VARCHAR(100)          DEFAULT NULL,
    `is_primary`   TINYINT      NOT NULL DEFAULT 0 COMMENT '主要联系人',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人';

CREATE TABLE IF NOT EXISTS `biz_follow_up` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `customer_id`  BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '跟进人',
    `type`         VARCHAR(20)  NOT NULL COMMENT 'call/visit/email/wechat/meeting',
    `content`      TEXT         NOT NULL COMMENT '跟进内容',
    `ai_summary`   TEXT                  DEFAULT NULL COMMENT 'AI 自动生成的摘要',
    `next_plan`    VARCHAR(500)          DEFAULT NULL COMMENT '下次计划',
    `next_time`    DATETIME              DEFAULT NULL COMMENT '下次跟进时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_customer_time` (`customer_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户跟进记录';

-- ======================== 3. 合同 ========================

CREATE TABLE IF NOT EXISTS `biz_contract` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT        NOT NULL,
    `contract_no`    VARCHAR(50)   NOT NULL COMMENT '合同编号',
    `title`          VARCHAR(200)  NOT NULL,
    `customer_id`    BIGINT                 DEFAULT NULL COMMENT '关联客户',
    `type`           VARCHAR(30)   NOT NULL COMMENT 'sales/purchase/service/other',
    `amount`         DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '合同金额',
    `currency`       VARCHAR(10)   NOT NULL DEFAULT 'CNY',
    `sign_date`      DATE                   DEFAULT NULL,
    `start_date`     DATE                   DEFAULT NULL,
    `end_date`       DATE                   DEFAULT NULL,
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'draft' COMMENT 'draft/pending/active/completed/terminated',
    `owner_id`       BIGINT                 DEFAULT NULL COMMENT '负责人',
    `file_key`       VARCHAR(300)           DEFAULT NULL COMMENT 'MinIO 文件 key',
    `ai_risk_score`  TINYINT                DEFAULT NULL COMMENT 'AI 风险评分 0-100',
    `ai_risk_note`   TEXT                   DEFAULT NULL COMMENT 'AI 风险提示',
    `remark`         TEXT                   DEFAULT NULL,
    `create_by`      BIGINT                 DEFAULT NULL,
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_contract_no` (`tenant_id`, `contract_no`),
    INDEX `idx_customer` (`customer_id`),
    INDEX `idx_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同';

CREATE TABLE IF NOT EXISTS `biz_contract_milestone` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT        NOT NULL,
    `contract_id`  BIGINT        NOT NULL,
    `name`         VARCHAR(100)  NOT NULL COMMENT '里程碑名称',
    `amount`       DECIMAL(15,2)          DEFAULT NULL COMMENT '关联金额',
    `due_date`     DATE                   DEFAULT NULL COMMENT '到期日',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending/completed/overdue',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_contract` (`contract_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同里程碑/回款节点';

-- ======================== 4. 工单 ========================

CREATE TABLE IF NOT EXISTS `biz_work_order` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `order_no`       VARCHAR(30)  NOT NULL COMMENT '工单号',
    `title`          VARCHAR(200) NOT NULL,
    `type`           VARCHAR(30)  NOT NULL COMMENT 'bug/feature/support/complaint/maintenance',
    `priority`       VARCHAR(10)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'open' COMMENT 'open/assigned/in_progress/resolved/closed',
    `customer_id`    BIGINT                DEFAULT NULL COMMENT '关联客户',
    `reporter_id`    BIGINT       NOT NULL COMMENT '提交人',
    `assignee_id`    BIGINT                DEFAULT NULL COMMENT '指派人',
    `dept_id`        BIGINT                DEFAULT NULL COMMENT '所属部门',
    `description`    TEXT                  DEFAULT NULL,
    `ai_category`    VARCHAR(50)           DEFAULT NULL COMMENT 'AI 自动分类',
    `ai_suggestion`  TEXT                  DEFAULT NULL COMMENT 'AI 处理建议',
    `sla_deadline`   DATETIME              DEFAULT NULL COMMENT 'SLA 截止时间',
    `resolved_at`    DATETIME              DEFAULT NULL,
    `closed_at`      DATETIME              DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_order_no` (`tenant_id`, `order_no`),
    INDEX `idx_assignee_status` (`assignee_id`, `status`),
    INDEX `idx_customer` (`customer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单';

CREATE TABLE IF NOT EXISTS `biz_work_order_comment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `order_id`     BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `content`      TEXT         NOT NULL,
    `is_internal`  TINYINT      NOT NULL DEFAULT 0 COMMENT '内部备注（客户不可见）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单评论';

-- ======================== 5. 审批流 ========================

CREATE TABLE IF NOT EXISTS `wf_definition` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `code`         VARCHAR(50)  NOT NULL COMMENT '流程编码',
    `name`         VARCHAR(100) NOT NULL,
    `nodes_json`   JSON         NOT NULL COMMENT '流程节点定义',
    `version`      INT          NOT NULL DEFAULT 1,
    `status`       TINYINT      NOT NULL DEFAULT 1,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_code_ver` (`tenant_id`, `code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义';

CREATE TABLE IF NOT EXISTS `wf_instance` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `definition_id`   BIGINT       NOT NULL,
    `biz_type`        VARCHAR(50)  NOT NULL COMMENT '业务类型',
    `biz_id`          BIGINT       NOT NULL COMMENT '关联业务记录 ID',
    `title`           VARCHAR(200) NOT NULL,
    `initiator_id`    BIGINT       NOT NULL COMMENT '发起人',
    `current_node`    VARCHAR(50)           DEFAULT NULL COMMENT '当前节点编码',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'running' COMMENT 'running/approved/rejected/canceled',
    `form_data`       JSON                  DEFAULT NULL COMMENT '表单快照',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`     DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_initiator` (`initiator_id`),
    INDEX `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程实例';

CREATE TABLE IF NOT EXISTS `wf_task` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `instance_id`  BIGINT       NOT NULL,
    `node_code`    VARCHAR(50)  NOT NULL COMMENT '节点编码',
    `node_name`    VARCHAR(100)          DEFAULT NULL,
    `assignee_id`  BIGINT       NOT NULL COMMENT '审批人',
    `action`       VARCHAR(20)           DEFAULT NULL COMMENT 'approve/reject/delegate/rollback',
    `comment`      TEXT                  DEFAULT NULL COMMENT '审批意见',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/completed',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`  DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_assignee_status` (`assignee_id`, `status`),
    INDEX `idx_instance` (`instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务';

-- ======================== 6. 工作派发 ========================

CREATE TABLE IF NOT EXISTS `biz_dispatch` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`       BIGINT       NOT NULL,
    `title`           VARCHAR(200) NOT NULL COMMENT '派发标题',
    `description`     TEXT                  DEFAULT NULL COMMENT '任务描述',
    `type`            VARCHAR(30)  NOT NULL DEFAULT 'task' COMMENT 'task/project/inspection/delivery',
    `priority`        VARCHAR(10)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    `dispatcher_id`   BIGINT       NOT NULL COMMENT '派发人',
    `dept_id`         BIGINT                DEFAULT NULL COMMENT '所属部门',
    `due_date`        DATETIME              DEFAULT NULL COMMENT '截止时间',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/in_progress/completed/rejected',
    `completion_note` TEXT                  DEFAULT NULL COMMENT '完成说明',
    `completed_at`    DATETIME              DEFAULT NULL,
    `biz_type`        VARCHAR(50)           DEFAULT NULL COMMENT '关联业务类型',
    `biz_id`          BIGINT                DEFAULT NULL COMMENT '关联业务 ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_tenant_status` (`tenant_id`, `status`),
    INDEX `idx_dispatcher` (`dispatcher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作派发';

CREATE TABLE IF NOT EXISTS `biz_dispatch_assignee` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `dispatch_id`  BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '被指派人',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/completed/rejected',
    `progress`     TINYINT      NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    `note`         TEXT                  DEFAULT NULL COMMENT '执行备注',
    `completed_at` DATETIME              DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_dispatch` (`dispatch_id`),
    INDEX `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派发指派人';

-- ======================== 7. 日程 & 提醒 ========================

CREATE TABLE IF NOT EXISTS `biz_schedule` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '所属用户',
    `title`        VARCHAR(200) NOT NULL,
    `description`  TEXT                  DEFAULT NULL,
    `type`         VARCHAR(30)  NOT NULL DEFAULT 'event' COMMENT 'event/meeting/reminder/follow_up',
    `start_time`   DATETIME     NOT NULL,
    `end_time`     DATETIME              DEFAULT NULL,
    `all_day`      TINYINT      NOT NULL DEFAULT 0 COMMENT '全天事件',
    `location`     VARCHAR(200)          DEFAULT NULL,
    `biz_type`     VARCHAR(50)           DEFAULT NULL COMMENT '关联业务',
    `biz_id`       BIGINT                DEFAULT NULL,
    `recurrence`   VARCHAR(20)           DEFAULT NULL COMMENT 'none/daily/weekly/monthly',
    `color`        VARCHAR(10)           DEFAULT NULL COMMENT '日历颜色标记',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active/completed/canceled',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_user_time` (`user_id`, `start_time`),
    INDEX `idx_tenant_time` (`tenant_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程';

CREATE TABLE IF NOT EXISTS `biz_schedule_participant` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `schedule_id`  BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL,
    `response`     VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/accepted/declined',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_schedule` (`schedule_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日程参与人';

CREATE TABLE IF NOT EXISTS `biz_task_reminder` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '提醒接收人',
    `title`        VARCHAR(200) NOT NULL,
    `content`      TEXT                  DEFAULT NULL,
    `remind_time`  DATETIME     NOT NULL COMMENT '提醒触发时间',
    `remind_type`  VARCHAR(20)  NOT NULL DEFAULT 'once' COMMENT 'once/daily/weekly',
    `channel`      VARCHAR(30)  NOT NULL DEFAULT 'web' COMMENT 'web/email/wechat/dingtalk',
    `biz_type`     VARCHAR(50)           DEFAULT NULL COMMENT '关联业务',
    `biz_id`       BIGINT                DEFAULT NULL,
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/sent/dismissed',
    `sent_at`      DATETIME              DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_time` (`user_id`, `remind_time`),
    INDEX `idx_status_time` (`status`, `remind_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务提醒';

-- ======================== 8. 通知 ========================

CREATE TABLE IF NOT EXISTS `sys_notification` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '接收人',
    `type`         VARCHAR(30)  NOT NULL COMMENT 'approval/mention/system/reminder/alert',
    `title`        VARCHAR(200) NOT NULL,
    `content`      TEXT                  DEFAULT NULL,
    `biz_type`     VARCHAR(50)           DEFAULT NULL,
    `biz_id`       BIGINT                DEFAULT NULL,
    `is_read`      TINYINT      NOT NULL DEFAULT 0,
    `channel`      VARCHAR(20)  NOT NULL DEFAULT 'web',
    `send_status`  VARCHAR(20)  NOT NULL DEFAULT 'sent',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_read` (`user_id`, `is_read`),
    INDEX `idx_tenant_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息';

CREATE TABLE IF NOT EXISTS `sys_notify_channel_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `channel`      VARCHAR(20)  NOT NULL COMMENT 'email/wechat_bot/dingtalk_bot/sms',
    `config_json`  JSON         NOT NULL COMMENT '通道配置',
    `enabled`      TINYINT      NOT NULL DEFAULT 1,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_channel` (`tenant_id`, `channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知通道配置';

-- ======================== 9. SSO ========================

CREATE TABLE IF NOT EXISTS `sys_oauth_provider` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`         BIGINT       NOT NULL,
    `provider`          VARCHAR(30)  NOT NULL COMMENT 'wechat_work/dingtalk/feishu/ldap/saml',
    `client_id`         VARCHAR(200)          DEFAULT NULL,
    `client_secret_enc` VARCHAR(500)          DEFAULT NULL COMMENT 'AES 加密',
    `config_json`       JSON                  DEFAULT NULL,
    `enabled`           TINYINT      NOT NULL DEFAULT 1,
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_provider` (`tenant_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方登录配置';

CREATE TABLE IF NOT EXISTS `sys_user_oauth` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT       NOT NULL,
    `provider`     VARCHAR(30)  NOT NULL,
    `open_id`      VARCHAR(200) NOT NULL COMMENT '第三方唯一 ID',
    `union_id`     VARCHAR(200)          DEFAULT NULL,
    `nickname`     VARCHAR(100)          DEFAULT NULL,
    `avatar_url`   VARCHAR(500)          DEFAULT NULL,
    `bind_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_provider_openid` (`provider`, `open_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户第三方账号绑定';

-- ======================== 10. API 开放平台 ========================

CREATE TABLE IF NOT EXISTS `open_api_key` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `name`         VARCHAR(100) NOT NULL COMMENT 'Key 用途说明',
    `key_prefix`   VARCHAR(10)  NOT NULL COMMENT 'Key 前缀（明文）',
    `key_hash`     VARCHAR(128) NOT NULL COMMENT 'SHA-256 哈希',
    `scope`        JSON         NOT NULL COMMENT '权限范围',
    `rate_limit`   INT          NOT NULL DEFAULT 60 COMMENT '每分钟最大请求数',
    `expires_at`   DATETIME              DEFAULT NULL,
    `last_used_at` DATETIME              DEFAULT NULL,
    `status`       TINYINT      NOT NULL DEFAULT 1,
    `create_by`    BIGINT                DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_key_hash` (`key_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开放 API Key';

CREATE TABLE IF NOT EXISTS `open_api_usage_log` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `api_key_id`   BIGINT       NOT NULL,
    `tenant_id`    BIGINT       NOT NULL,
    `endpoint`     VARCHAR(100) NOT NULL,
    `method`       VARCHAR(10)           DEFAULT NULL,
    `status_code`  INT                   DEFAULT NULL,
    `latency_ms`   INT                   DEFAULT NULL,
    `token_used`   INT                   DEFAULT NULL COMMENT 'AI token 消耗',
    `ip`           VARCHAR(45)           DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_key_time` (`api_key_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 调用日志';

-- ======================== 11. 文档导出任务 ========================

CREATE TABLE IF NOT EXISTS `export_task` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`    BIGINT       NOT NULL,
    `user_id`      BIGINT       NOT NULL COMMENT '发起人',
    `biz_type`     VARCHAR(50)  NOT NULL COMMENT '导出类型 customer/contract/work_order/session',
    `biz_id`       BIGINT                DEFAULT NULL COMMENT '业务 ID',
    `format`       VARCHAR(10)  NOT NULL DEFAULT 'pdf' COMMENT 'pdf/docx',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
    `file_key`     VARCHAR(300)          DEFAULT NULL COMMENT 'MinIO 存储 key',
    `file_name`    VARCHAR(200)          DEFAULT NULL COMMENT '文件名',
    `error_msg`    TEXT                  DEFAULT NULL,
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finish_time`  DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档导出任务';

-- ======================== 12. 新增权限 ========================

INSERT IGNORE INTO `sys_permission`(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`) VALUES
    -- CRM
    (5001, 0,    'crm:menu',            'CRM 客户管理',     'menu', '/crm',                  NULL,    50, 1),
    (5002, 5001, 'crm:customer:list',   '查看客户列表',     'api',  '/crm/customers',        'GET',   51, 1),
    (5003, 5001, 'crm:customer:create', '创建客户',         'api',  '/crm/customers',        'POST',  52, 1),
    (5004, 5001, 'crm:customer:edit',   '编辑客户',         'api',  '/crm/customers/*',      'PUT',   53, 1),
    (5005, 5001, 'crm:customer:delete', '删除客户',         'api',  '/crm/customers/*',      'DELETE',54, 1),
    (5006, 5001, 'crm:followup:create', '新增跟进',         'api',  '/crm/follow-ups',       'POST',  55, 1),
    -- 合同
    (5101, 0,    'contract:menu',           '合同管理',       'menu', '/contracts',            NULL,    55, 1),
    (5102, 5101, 'contract:list',           '查看合同列表',   'api',  '/contracts',            'GET',   56, 1),
    (5103, 5101, 'contract:create',         '创建合同',       'api',  '/contracts',            'POST',  57, 1),
    (5104, 5101, 'contract:edit',           '编辑合同',       'api',  '/contracts/*',          'PUT',   58, 1),
    -- 工单
    (5201, 0,    'workorder:menu',          '工单管理',       'menu', '/work-orders',          NULL,    60, 1),
    (5202, 5201, 'workorder:list',          '查看工单',       'api',  '/work-orders',          'GET',   61, 1),
    (5203, 5201, 'workorder:create',        '创建工单',       'api',  '/work-orders',          'POST',  62, 1),
    (5204, 5201, 'workorder:edit',          '编辑工单',       'api',  '/work-orders/*',        'PUT',   63, 1),
    -- 审批
    (5301, 0,    'workflow:menu',           '审批管理',       'menu', '/workflow',              NULL,    65, 1),
    (5302, 5301, 'workflow:task:list',      '查看待办',       'api',  '/workflow/tasks',       'GET',   66, 1),
    (5303, 5301, 'workflow:task:action',    '审批操作',       'api',  '/workflow/tasks/*',     'POST',  67, 1),
    (5304, 5301, 'workflow:def:manage',     '管理流程定义',   'api',  '/workflow/definitions', 'POST',  68, 1),
    -- 派发
    (5401, 0,    'dispatch:menu',           '工作派发',       'menu', '/dispatch',              NULL,    70, 1),
    (5402, 5401, 'dispatch:list',           '查看派发',       'api',  '/dispatch',              'GET',   71, 1),
    (5403, 5401, 'dispatch:create',         '创建派发',       'api',  '/dispatch',              'POST',  72, 1),
    (5404, 5401, 'dispatch:edit',           '编辑派发',       'api',  '/dispatch/*',            'PUT',   73, 1),
    -- 日程
    (5501, 0,    'schedule:menu',           '日程管理',       'menu', '/schedule',              NULL,    75, 1),
    (5502, 5501, 'schedule:list',           '查看日程',       'api',  '/schedule',              'GET',   76, 1),
    (5503, 5501, 'schedule:create',         '创建日程',       'api',  '/schedule',              'POST',  77, 1),
    (5504, 5501, 'schedule:edit',           '编辑日程',       'api',  '/schedule/*',            'PUT',   78, 1),
    -- 导出
    (5601, 0,    'export:menu',             '文档导出',       'menu', '/export',                NULL,    80, 1),
    (5602, 5601, 'export:create',           '发起导出',       'api',  '/export',                'POST',  81, 1),
    -- 通知
    (5701, 0,    'notification:menu',       '通知中心',       'menu', '/notifications',         NULL,    85, 1),
    (5702, 5701, 'notification:list',       '查看通知',       'api',  '/notifications',         'GET',   86, 1);

-- SUPER_ADMIN 获得所有新权限
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (3000000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id >= 5001 AND p.id <= 5702;

-- TENANT_ADMIN 获得所有新权限（除流程定义管理外）
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (4000000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p WHERE p.id >= 5001 AND p.id <= 5702 AND p.id != 5304;

-- KB_ADMIN 获得 CRM 查看 + 工单 + 派发 + 日程 + 通知
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (6000001, 3, 5001),(6000002, 3, 5002),
    (6000003, 3, 5201),(6000004, 3, 5202),(6000005, 3, 5203),
    (6000006, 3, 5401),(6000007, 3, 5402),(6000008, 3, 5403),
    (6000009, 3, 5501),(6000010, 3, 5502),(6000011, 3, 5503),
    (6000012, 3, 5601),(6000013, 3, 5602),
    (6000014, 3, 5701),(6000015, 3, 5702);

-- STAFF 获得日程 + 派发接收 + 通知
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (7000001, 4, 5301),(7000002, 4, 5302),(7000003, 4, 5303),
    (7000004, 4, 5401),(7000005, 4, 5402),
    (7000006, 4, 5501),(7000007, 4, 5502),(7000008, 4, 5503),(7000009, 4, 5504),
    (7000010, 4, 5601),(7000011, 4, 5602),
    (7000012, 4, 5701),(7000013, 4, 5702);

-- ======================== 13. 租户忽略表更新 ========================
-- 以下表需要跨租户查询场景，在 MyBatis-Plus TenantLineInnerInterceptor 忽略列表中追加
-- （需在 application.yaml 的 tenant.ignore-tables 中配置）
-- biz_dispatch_assignee, biz_schedule_participant, biz_work_order_comment,
-- wf_task, open_api_usage_log, export_task, sys_notification

-- ======================== 验证 ========================
SELECT '--- 新建表统计 ---' AS info;
SELECT COUNT(*) AS new_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'zwskchat' AND TABLE_NAME IN (
    'ai_data_source','ai_sql_execution_log',
    'biz_customer','biz_contact','biz_follow_up',
    'biz_contract','biz_contract_milestone',
    'biz_work_order','biz_work_order_comment',
    'wf_definition','wf_instance','wf_task',
    'biz_dispatch','biz_dispatch_assignee',
    'biz_schedule','biz_schedule_participant','biz_task_reminder',
    'sys_notification','sys_notify_channel_config',
    'sys_oauth_provider','sys_user_oauth',
    'open_api_key','open_api_usage_log',
    'export_task'
);
