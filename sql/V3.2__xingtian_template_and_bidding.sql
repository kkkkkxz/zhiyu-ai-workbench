-- =====================================================================
-- V3.2 · 四川兴天建设项目管理有限公司专属模板与标书生成引擎
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;

-- 1. 注册四川兴天专属行业模板
INSERT IGNORE INTO `tpl_package`
(`id`, `code`, `name`, `category`, `description`, `scenario`, `modules_json`, `ai_agents_json`, `sort`)
VALUES
(4, 'xingtian-pm', '四川兴天项目管理模板', 'project',
 '为工程咨询、监理、造价及招标代理量身打造，内置智能标书生成、造价二审三审流程和示范项目数据。',
 '工程监理规划、造价一审二审、招标标书一键生成与大纲装配',
 JSON_ARRAY('工程项目', '监理日志', '造价审核', '招标代理', '智能标书'),
 JSON_ARRAY('兴天标书助手', '工程造价精算师', '项目总监助理'),
 40);

-- 2. 初始版本种子
INSERT IGNORE INTO `tpl_package_version`(`id`, `package_code`, `version`, `changelog`, `status`)
VALUES
(4, 'xingtian-pm', '1.0.0', '初始版本：发布智能标书大纲生成、一审二审流程以及工程监理配套功能。', 1);
