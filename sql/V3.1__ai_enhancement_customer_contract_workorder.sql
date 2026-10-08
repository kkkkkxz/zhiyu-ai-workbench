-- =====================================================================
-- V3.1 · AI 业务增强：客户评分、生命周期、回款提醒、工单智能分配
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- ======================== 1. 客户AI增强字段 ========================
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程包装
DROP PROCEDURE IF EXISTS _v31_add_customer_cols;
DELIMITER //
CREATE PROCEDURE _v31_add_customer_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='biz_customer' AND COLUMN_NAME='grade') THEN
        ALTER TABLE `biz_customer`
            ADD COLUMN `grade`             VARCHAR(10)   DEFAULT NULL COMMENT '客户等级 S/A/B/C/D' AFTER `status`,
            ADD COLUMN `ai_score`          INT           DEFAULT NULL COMMENT 'AI 综合评分 0-100' AFTER `grade`,
            ADD COLUMN `lifecycle_stage`   VARCHAR(30)   DEFAULT NULL COMMENT '生命周期 new/potential/key/dormant/lost' AFTER `ai_score`,
            ADD COLUMN `ai_next_action`    VARCHAR(500)  DEFAULT NULL COMMENT 'AI 建议下一步动作' AFTER `lifecycle_stage`,
            ADD COLUMN `ai_value_predict`  DECIMAL(15,2) DEFAULT NULL COMMENT 'AI 预测客户年价值' AFTER `ai_next_action`,
            ADD COLUMN `ai_scored_at`      DATETIME      DEFAULT NULL COMMENT '最近评分时间' AFTER `ai_value_predict`,
            ADD COLUMN `last_follow_at`    DATETIME      DEFAULT NULL COMMENT '最近跟进时间' AFTER `ai_scored_at`;
    END IF;
END //
DELIMITER ;
CALL _v31_add_customer_cols();
DROP PROCEDURE IF EXISTS _v31_add_customer_cols;

-- ======================== 2. 客户评分日志 ========================
CREATE TABLE IF NOT EXISTS `biz_customer_score_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `customer_id`    BIGINT       NOT NULL,
    `old_grade`      VARCHAR(10)           DEFAULT NULL,
    `new_grade`      VARCHAR(10)           DEFAULT NULL,
    `old_score`      INT                   DEFAULT NULL,
    `new_score`      INT                   DEFAULT NULL,
    `old_stage`      VARCHAR(30)           DEFAULT NULL,
    `new_stage`      VARCHAR(30)           DEFAULT NULL,
    `reason`         VARCHAR(500)          DEFAULT NULL COMMENT 'AI 评分理由',
    `next_action`    VARCHAR(500)          DEFAULT NULL COMMENT 'AI 建议动作',
    `scored_by`      VARCHAR(20)  NOT NULL DEFAULT 'ai' COMMENT 'ai/manual',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_customer` (`tenant_id`, `customer_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户评分变更日志';

-- ======================== 3. 回款提醒记录 ========================
CREATE TABLE IF NOT EXISTS `biz_payment_reminder` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `contract_id`    BIGINT       NOT NULL,
    `milestone_id`   BIGINT                DEFAULT NULL,
    `customer_id`    BIGINT                DEFAULT NULL,
    `title`          VARCHAR(200) NOT NULL COMMENT '提醒标题',
    `amount`         DECIMAL(15,2)         DEFAULT NULL COMMENT '应收金额',
    `due_date`       DATE         NOT NULL COMMENT '到期日',
    `priority`       VARCHAR(10)  NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    `ai_risk_level`  VARCHAR(20)           DEFAULT NULL COMMENT '逾期风险 LOW/MEDIUM/HIGH',
    `ai_suggestion`  VARCHAR(500)          DEFAULT NULL COMMENT 'AI 催款建议',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/notified/collected/overdue',
    `notified_at`    DATETIME              DEFAULT NULL,
    `collected_at`   DATETIME              DEFAULT NULL,
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_contract` (`tenant_id`, `contract_id`),
    INDEX `idx_due` (`tenant_id`, `due_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款提醒';

-- ======================== 4. 工单AI增强字段 ========================
DROP PROCEDURE IF EXISTS _v31_add_workorder_cols;
DELIMITER //
CREATE PROCEDURE _v31_add_workorder_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zwskchat' AND TABLE_NAME='biz_work_order' AND COLUMN_NAME='ai_assigned_reason') THEN
        ALTER TABLE `biz_work_order`
            ADD COLUMN `ai_assigned_reason`  VARCHAR(500)  DEFAULT NULL COMMENT 'AI 自动分配理由' AFTER `ai_suggestion`,
            ADD COLUMN `ai_similar_ids`      VARCHAR(200)  DEFAULT NULL COMMENT 'AI 检索到的相似工单ID(逗号分隔)' AFTER `ai_assigned_reason`,
            ADD COLUMN `ai_resolution_hint`  TEXT          DEFAULT NULL COMMENT 'AI 解决方案建议' AFTER `ai_similar_ids`;
    END IF;
END //
DELIMITER ;
CALL _v31_add_workorder_cols();
DROP PROCEDURE IF EXISTS _v31_add_workorder_cols;

-- ======================== 5. BI 日汇总快照 ========================
CREATE TABLE IF NOT EXISTS `biz_dashboard_snapshot` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `tenant_id`      BIGINT       NOT NULL,
    `snapshot_date`  DATE         NOT NULL,
    `data_type`      VARCHAR(50)  NOT NULL COMMENT 'customer_summary/contract_summary/workorder_summary/dispatch_summary',
    `data_json`      JSON         NOT NULL COMMENT '聚合数据',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_tenant_date_type` (`tenant_id`, `snapshot_date`, `data_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI 日汇总快照';

-- ======================== 6. 扩展AI动作定义 ========================
INSERT IGNORE INTO `ai_action_definition`
(`id`, `tenant_id`, `action_code`, `name`, `description`, `biz_type`, `risk_level`, `permission_code`, `param_schema`, `confirm_required`, `sort`)
VALUES
(12, 0, 'SCORE_CUSTOMER', '客户智能评分', '对指定客户进行AI综合评分和等级评估', 'customer', 'LOW', 'crm:customer:edit',
 '{"type":"object","properties":{"customerName":{"type":"string","description":"客户名称"}}}',
 0, 120),
(13, 0, 'ANALYZE_CONTRACT_RISK', '合同风险分析', '对指定合同进行AI风险条款分析', 'contract', 'LOW', 'contract:list',
 '{"type":"object","properties":{"contractNo":{"type":"string","description":"合同编号"}}}',
 0, 130),
(14, 0, 'GENERATE_PAYMENT_REMINDER', '生成回款提醒', '扫描即将到期的合同回款节点生成提醒', 'contract', 'LOW', 'contract:list',
 '{"type":"object","properties":{"daysAhead":{"type":"integer","description":"提前天数","default":7}}}',
 0, 140),
(15, 0, 'SMART_ASSIGN_WORKORDER', '智能分配工单', '根据员工负载和技能AI分配工单', 'work_order', 'MEDIUM', 'workorder:edit',
 '{"type":"object","properties":{"workOrderNo":{"type":"string","description":"工单编号"}}}',
 1, 150),
(16, 0, 'GENERATE_BIZ_REPORT', '生成业务报表', '生成指定类型的AI业务分析报表', 'report', 'LOW', 'crm:customer:list',
 '{"type":"object","properties":{"reportType":{"type":"string","enum":["sales","contract","workorder","dispatch"],"description":"报表类型"},"period":{"type":"string","enum":["today","week","month"],"description":"时间范围"}}}',
 0, 160),
(17, 0, 'APPROVAL_SUGGESTION', '审批智能建议', '对待审批事项生成AI建议', 'workflow', 'LOW', 'workflow:task:list',
 '{"type":"object","properties":{"taskId":{"type":"integer","description":"审批任务ID"}}}',
 0, 170),
(18, 0, 'SMART_SCHEDULE', '智能日程生成', '根据当前任务和合同自动生成待办日程', 'schedule', 'LOW', 'schedule:create',
 '{"type":"object","properties":{}}',
 1, 180);

-- ======================== 验证 ========================
SELECT '--- V3.1 新建表统计 ---' AS info;
SELECT COUNT(*) AS new_table_count FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'zwskchat' AND TABLE_NAME IN (
    'biz_customer_score_log', 'biz_payment_reminder', 'biz_dashboard_snapshot'
);
