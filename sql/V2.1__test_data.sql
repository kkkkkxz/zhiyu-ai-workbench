-- =====================================================================
-- V2.1 · 多租户 ERP/AI 测试数据
-- 说明：1 个平台租户 + 3 个企业租户，每个企业约 30 人，业务数据按 tenant_id 隔离
-- 演示账号不提供源码默认密码；密码需在部署环境另行设置。
-- =====================================================================
SET NAMES utf8mb4;
USE `zwskchat`;
SET FOREIGN_KEY_CHECKS = 0;

-- ======================== 1. 平台租户与企业租户 ========================
UPDATE `sys_tenant`
SET `code` = 'platform',
    `name` = '平台管理租户',
    `plan_code` = 'platform',
    `max_users` = 20,
    `max_storage_mb` = 10240,
    `remark` = '平台租户，仅用于租户管理员查看企业 Boss 数量，不承载 ERP 业务数据',
    `status` = 1,
    `deleted` = 0
WHERE `id` = 1;

INSERT INTO `sys_tenant`
(`id`, `code`, `name`, `status`, `plan_code`, `max_users`, `max_storage_mb`, `expires_at`, `remark`, `deleted`) VALUES
(2, 'salesco', '星河销售服务有限公司', 1, 'standard', 100, 20480, NULL, '销售型企业，销售、渠道、客服占主体', 0),
(3, 'devco',   '云启软件研发有限公司', 1, 'standard', 100, 40960, NULL, '软件开发企业，研发、测试、运维交付占主体', 0),
(4, 'educo',   '知行教育培训有限公司', 1, 'standard', 100, 20480, NULL, '教育培训企业，教研、讲师、招生咨询占主体', 0)
ON DUPLICATE KEY UPDATE
`code` = VALUES(`code`), `name` = VALUES(`name`), `status` = VALUES(`status`),
`plan_code` = VALUES(`plan_code`), `max_users` = VALUES(`max_users`),
`max_storage_mb` = VALUES(`max_storage_mb`), `remark` = VALUES(`remark`), `deleted` = 0;

-- ======================== 2. 角色 ========================
INSERT INTO `sys_role`
(`id`, `tenant_id`, `role_code`, `role_name`, `description`, `status`, `deleted`) VALUES
(2101, 2, 'SUPER_ADMIN',  '公司 Boss',     '企业最高管理员，可使用 ERP 与 AI 功能', 1, 0),
(2102, 2, 'TENANT_ADMIN', '企业用户管理员', '企业内用户管理角色，默认不分配给员工', 1, 0),
(2103, 2, 'KB_ADMIN',     '知识库管理员',   '管理本企业知识库', 1, 0),
(2104, 2, 'STAFF',        '普通员工',       '普通业务员工', 1, 0),
(2105, 2, 'AUDITOR',      '审计人员',       '查看审计与统计数据', 1, 0),
(3101, 3, 'SUPER_ADMIN',  '公司 Boss',     '企业最高管理员，可使用 ERP 与 AI 功能', 1, 0),
(3102, 3, 'TENANT_ADMIN', '企业用户管理员', '企业内用户管理角色，默认不分配给员工', 1, 0),
(3103, 3, 'KB_ADMIN',     '知识库管理员',   '管理本企业知识库', 1, 0),
(3104, 3, 'STAFF',        '普通员工',       '普通研发员工', 1, 0),
(3105, 3, 'AUDITOR',      '审计人员',       '查看审计与统计数据', 1, 0),
(4101, 4, 'SUPER_ADMIN',  '公司 Boss',     '企业最高管理员，可使用 ERP 与 AI 功能', 1, 0),
(4102, 4, 'TENANT_ADMIN', '企业用户管理员', '企业内用户管理角色，默认不分配给员工', 1, 0),
(4103, 4, 'KB_ADMIN',     '知识库管理员',   '管理本企业知识库', 1, 0),
(4104, 4, 'STAFF',        '普通员工',       '普通教学与运营员工', 1, 0),
(4105, 4, 'AUDITOR',      '审计人员',       '查看审计与统计数据', 1, 0)
ON DUPLICATE KEY UPDATE
`role_name` = VALUES(`role_name`), `description` = VALUES(`description`), `status` = 1, `deleted` = 0;

-- 平台租户管理员：只进入独立租户管理页，查看各企业公司 Boss 数量
INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `phone`, `email`, `dept_id`, `status`, `deleted`) VALUES
(20, 1, 'tenantadmin', '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '平台租户管理员', '13900000020', 'tenantadmin@platform.demo', NULL, 1, 0)
ON DUPLICATE KEY UPDATE
`tenant_id` = VALUES(`tenant_id`), `password` = VALUES(`password`), `nickname` = VALUES(`nickname`), `status` = 1, `deleted` = 0;

INSERT IGNORE INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`) VALUES
(200020, 1, 20, 2);

-- 新企业角色权限：Boss 拿全部权限，其他企业角色继承平台同名基础角色的权限集合
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT('v21:super:', r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED), r.id, p.id
FROM `sys_role` r
JOIN `sys_permission` p ON p.status = 1
WHERE r.id IN (2101, 3101, 4101);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT('v21:tenant-admin:', r.id, ':', rp.permission_id)), 1, 15), 16, 10) AS UNSIGNED), r.id, rp.permission_id
FROM `sys_role` r
JOIN `sys_role_permission` rp ON rp.role_id = 2
WHERE r.id IN (2102, 3102, 4102);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT('v21:kb-admin:', r.id, ':', rp.permission_id)), 1, 15), 16, 10) AS UNSIGNED), r.id, rp.permission_id
FROM `sys_role` r
JOIN `sys_role_permission` rp ON rp.role_id = 3
WHERE r.id IN (2103, 3103, 4103);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT('v21:staff:', r.id, ':', rp.permission_id)), 1, 15), 16, 10) AS UNSIGNED), r.id, rp.permission_id
FROM `sys_role` r
JOIN `sys_role_permission` rp ON rp.role_id = 4
WHERE r.id IN (2104, 3104, 4104);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT('v21:auditor:', r.id, ':', rp.permission_id)), 1, 15), 16, 10) AS UNSIGNED), r.id, rp.permission_id
FROM `sys_role` r
JOIN `sys_role_permission` rp ON rp.role_id = 5
WHERE r.id IN (2105, 3105, 4105);

-- ======================== 3. 部门 ========================
INSERT INTO `sys_dept`
(`id`, `tenant_id`, `parent_id`, `name`, `code`, `sort`, `status`, `leader_user_id`, `path`, `level`, `deleted`) VALUES
(200, 2, 0,   '星河销售服务有限公司', 'SALES_ROOT', 0, 1, 2001, '/200', 1, 0),
(201, 2, 200, '总经办',               'SALES_EXEC', 1, 1, 2001, '/200/201', 2, 0),
(202, 2, 200, '销售一部',             'SALES_A',    2, 1, 2002, '/200/202', 2, 0),
(203, 2, 200, '销售二部',             'SALES_B',    3, 1, 2010, '/200/203', 2, 0),
(204, 2, 200, '渠道部',               'SALES_CH',   4, 1, 2018, '/200/204', 2, 0),
(205, 2, 200, '客户成功部',           'SALES_CS',   5, 1, 2023, '/200/205', 2, 0),
(206, 2, 200, '市场部',               'SALES_MKT',  6, 1, 2027, '/200/206', 2, 0),
(207, 2, 200, '财务行政部',           'SALES_FA',   7, 1, 2030, '/200/207', 2, 0),
(300, 3, 0,   '云启软件研发有限公司', 'DEV_ROOT',   0, 1, 3001, '/300', 1, 0),
(301, 3, 300, '总经办',               'DEV_EXEC',   1, 1, 3001, '/300/301', 2, 0),
(302, 3, 300, '产品部',               'DEV_PD',     2, 1, 3002, '/300/302', 2, 0),
(303, 3, 300, '前端组',               'DEV_FE',     3, 1, 3006, '/300/303', 2, 0),
(304, 3, 300, '后端组',               'DEV_BE',     4, 1, 3011, '/300/304', 2, 0),
(305, 3, 300, '测试部',               'DEV_QA',     5, 1, 3018, '/300/305', 2, 0),
(306, 3, 300, '运维交付部',           'DEV_OPS',    6, 1, 3023, '/300/306', 2, 0),
(307, 3, 300, '项目管理部',           'DEV_PM',     7, 1, 3027, '/300/307', 2, 0),
(400, 4, 0,   '知行教育培训有限公司', 'EDU_ROOT',   0, 1, 4001, '/400', 1, 0),
(401, 4, 400, '校长办',               'EDU_EXEC',   1, 1, 4001, '/400/401', 2, 0),
(402, 4, 400, '教研部',               'EDU_RD',     2, 1, 4002, '/400/402', 2, 0),
(403, 4, 400, '讲师一部',             'EDU_TA',     3, 1, 4007, '/400/403', 2, 0),
(404, 4, 400, '讲师二部',             'EDU_TB',     4, 1, 4015, '/400/404', 2, 0),
(405, 4, 400, '招生咨询部',           'EDU_ADM',    5, 1, 4021, '/400/405', 2, 0),
(406, 4, 400, '教务运营部',           'EDU_OPS',    6, 1, 4026, '/400/406', 2, 0),
(407, 4, 400, '市场推广部',           'EDU_MKT',    7, 1, 4029, '/400/407', 2, 0)
ON DUPLICATE KEY UPDATE
`name` = VALUES(`name`), `code` = VALUES(`code`), `parent_id` = VALUES(`parent_id`),
`sort` = VALUES(`sort`), `status` = 1, `leader_user_id` = VALUES(`leader_user_id`),
`path` = VALUES(`path`), `level` = VALUES(`level`), `deleted` = 0;

-- ======================== 4. 用户，每家 30 人 ========================
DROP TEMPORARY TABLE IF EXISTS `tmp_v21_seq`;
CREATE TEMPORARY TABLE `tmp_v21_seq` (`n` INT NOT NULL PRIMARY KEY);
INSERT INTO `tmp_v21_seq`(`n`) VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),(15),
(16),(17),(18),(19),(20),(21),(22),(23),(24),(25),(26),(27),(28),(29),(30);

INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `phone`, `email`, `dept_id`, `status`, `deleted`)
SELECT 2000 + n, 2,
       CASE WHEN n = 1 THEN 'sales_boss' ELSE CONCAT('sales_', LPAD(n, 2, '0')) END,
       '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e',
       CASE WHEN n = 1 THEN '陈星河 Boss' ELSE CONCAT('星河销售员工', LPAD(n, 2, '0')) END,
       CONCAT('1382000', LPAD(n, 4, '0')),
       CASE WHEN n = 1 THEN 'boss@salesco.demo' ELSE CONCAT('sales_', LPAD(n, 2, '0'), '@salesco.demo') END,
       CASE
         WHEN n = 1 THEN 201
         WHEN n BETWEEN 2 AND 9 THEN 202
         WHEN n BETWEEN 10 AND 17 THEN 203
         WHEN n BETWEEN 18 AND 22 THEN 204
         WHEN n BETWEEN 23 AND 26 THEN 205
         WHEN n BETWEEN 27 AND 29 THEN 206
         ELSE 207
       END,
       1, 0
FROM `tmp_v21_seq`
ON DUPLICATE KEY UPDATE
`password` = VALUES(`password`), `nickname` = VALUES(`nickname`), `phone` = VALUES(`phone`),
`email` = VALUES(`email`), `dept_id` = VALUES(`dept_id`), `status` = 1, `deleted` = 0;

INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `phone`, `email`, `dept_id`, `status`, `deleted`)
SELECT 3000 + n, 3,
       CASE WHEN n = 1 THEN 'dev_boss' ELSE CONCAT('dev_', LPAD(n, 2, '0')) END,
       '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e',
       CASE WHEN n = 1 THEN '林云启 Boss' ELSE CONCAT('云启研发员工', LPAD(n, 2, '0')) END,
       CONCAT('1383000', LPAD(n, 4, '0')),
       CASE WHEN n = 1 THEN 'boss@devco.demo' ELSE CONCAT('dev_', LPAD(n, 2, '0'), '@devco.demo') END,
       CASE
         WHEN n = 1 THEN 301
         WHEN n BETWEEN 2 AND 5 THEN 302
         WHEN n BETWEEN 6 AND 10 THEN 303
         WHEN n BETWEEN 11 AND 17 THEN 304
         WHEN n BETWEEN 18 AND 22 THEN 305
         WHEN n BETWEEN 23 AND 26 THEN 306
         ELSE 307
       END,
       1, 0
FROM `tmp_v21_seq`
ON DUPLICATE KEY UPDATE
`password` = VALUES(`password`), `nickname` = VALUES(`nickname`), `phone` = VALUES(`phone`),
`email` = VALUES(`email`), `dept_id` = VALUES(`dept_id`), `status` = 1, `deleted` = 0;

INSERT INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `phone`, `email`, `dept_id`, `status`, `deleted`)
SELECT 4000 + n, 4,
       CASE WHEN n = 1 THEN 'edu_boss' ELSE CONCAT('edu_', LPAD(n, 2, '0')) END,
       '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e',
       CASE WHEN n = 1 THEN '周知行 Boss' ELSE CONCAT('知行教育员工', LPAD(n, 2, '0')) END,
       CONCAT('1384000', LPAD(n, 4, '0')),
       CASE WHEN n = 1 THEN 'boss@educo.demo' ELSE CONCAT('edu_', LPAD(n, 2, '0'), '@educo.demo') END,
       CASE
         WHEN n = 1 THEN 401
         WHEN n BETWEEN 2 AND 6 THEN 402
         WHEN n BETWEEN 7 AND 14 THEN 403
         WHEN n BETWEEN 15 AND 20 THEN 404
         WHEN n BETWEEN 21 AND 25 THEN 405
         WHEN n BETWEEN 26 AND 28 THEN 406
         ELSE 407
       END,
       1, 0
FROM `tmp_v21_seq`
ON DUPLICATE KEY UPDATE
`password` = VALUES(`password`), `nickname` = VALUES(`nickname`), `phone` = VALUES(`phone`),
`email` = VALUES(`email`), `dept_id` = VALUES(`dept_id`), `status` = 1, `deleted` = 0;

INSERT IGNORE INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`)
SELECT 220000 + n, 2, 2000 + n, CASE WHEN n = 1 THEN 2101 ELSE 2104 END FROM `tmp_v21_seq`;
INSERT IGNORE INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`)
SELECT 330000 + n, 3, 3000 + n, CASE WHEN n = 1 THEN 3101 ELSE 3104 END FROM `tmp_v21_seq`;
INSERT IGNORE INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`)
SELECT 440000 + n, 4, 4000 + n, CASE WHEN n = 1 THEN 4101 ELSE 4104 END FROM `tmp_v21_seq`;

INSERT IGNORE INTO `sys_user_reporting`(`id`, `tenant_id`, `user_id`, `manager_user_id`, `dept_id`, `relation_type`)
SELECT 2200000 + n, 2, 2000 + n, 2001,
       CASE
         WHEN n BETWEEN 2 AND 9 THEN 202
         WHEN n BETWEEN 10 AND 17 THEN 203
         WHEN n BETWEEN 18 AND 22 THEN 204
         WHEN n BETWEEN 23 AND 26 THEN 205
         WHEN n BETWEEN 27 AND 29 THEN 206
         ELSE 207
       END,
       'DIRECT'
FROM `tmp_v21_seq` WHERE n > 1;

INSERT IGNORE INTO `sys_user_reporting`(`id`, `tenant_id`, `user_id`, `manager_user_id`, `dept_id`, `relation_type`)
SELECT 3300000 + n, 3, 3000 + n, 3001,
       CASE
         WHEN n BETWEEN 2 AND 5 THEN 302
         WHEN n BETWEEN 6 AND 10 THEN 303
         WHEN n BETWEEN 11 AND 17 THEN 304
         WHEN n BETWEEN 18 AND 22 THEN 305
         WHEN n BETWEEN 23 AND 26 THEN 306
         ELSE 307
       END,
       'DIRECT'
FROM `tmp_v21_seq` WHERE n > 1;

INSERT IGNORE INTO `sys_user_reporting`(`id`, `tenant_id`, `user_id`, `manager_user_id`, `dept_id`, `relation_type`)
SELECT 4400000 + n, 4, 4000 + n, 4001,
       CASE
         WHEN n BETWEEN 2 AND 6 THEN 402
         WHEN n BETWEEN 7 AND 14 THEN 403
         WHEN n BETWEEN 15 AND 20 THEN 404
         WHEN n BETWEEN 21 AND 25 THEN 405
         WHEN n BETWEEN 26 AND 28 THEN 406
         ELSE 407
       END,
       'DIRECT'
FROM `tmp_v21_seq` WHERE n > 1;

-- ======================== 5. CRM 客户与联系人 ========================
INSERT IGNORE INTO `biz_customer`
(`id`, `tenant_id`, `name`, `short_name`, `type`, `industry`, `scale`, `source`, `owner_id`, `status`, `phone`, `email`, `address`, `remark`, `create_by`) VALUES
(20001, 2, '杭州云贸科技有限公司', '云贸科技', 'enterprise', '电商', 'medium', 'exhibition', 2002, 'active', '0571-88000001', 'contact@yunmao.demo', '杭州市余杭区未来科技城', '销售型企业重点客户', 2001),
(20002, 2, '苏州智造设备有限公司', '苏州智造', 'enterprise', '制造业', 'large', 'referral', 2010, 'active', '0512-68000002', 'sales@szzhizao.demo', '苏州市工业园区', '渠道合作客户', 2001),
(20003, 2, '上海百川零售集团', '百川零售', 'enterprise', '零售', 'large', 'website', 2018, 'potential', '021-66000003', 'info@baichuan.demo', '上海市徐汇区漕河泾', '待二次拜访', 2001),
(20004, 2, '南京云仓供应链有限公司', '云仓供应链', 'enterprise', '物流供应链', 'medium', 'cold_call', 2003, 'active', '025-85000004', 'bd@yuncang.demo', '南京市江宁区', '续约概率高', 2001),
(20005, 2, '广州海星贸易有限公司', '海星贸易', 'enterprise', '批发贸易', 'small', 'referral', 2011, 'potential', '020-88000005', 'hello@haixing.demo', '广州市天河区', '需要价格方案', 2001),
(20006, 2, '北京新锐消费品牌', '新锐消费', 'enterprise', '消费品', 'medium', 'website', 2024, 'active', '010-88000006', 'ops@xinrui.demo', '北京市朝阳区', '客户成功跟进中', 2001),
(30001, 3, '深圳星链金融科技', '星链金科', 'enterprise', '金融科技', 'large', 'referral', 3027, 'active', '0755-86000001', 'it@starlink.demo', '深圳市南山区科技园', '软件研发项目客户', 3001),
(30002, 3, '北京数智医疗平台', '数智医疗', 'enterprise', '医疗信息化', 'medium', 'website', 3002, 'active', '010-66000002', 'pm@meddata.demo', '北京市海淀区', '医疗 SaaS 项目', 3001),
(30003, 3, '成都车联网研究院', '车联研究院', 'enterprise', '车联网', 'medium', 'exhibition', 3011, 'potential', '028-85000003', 'rd@iov.demo', '成都市高新区', '需要原型评审', 3001),
(30004, 3, '上海工业互联网中心', '工互中心', 'enterprise', '工业互联网', 'large', 'referral', 3012, 'active', '021-67000004', 'ops@iiot.demo', '上海市浦东新区', '交付阶段', 3001),
(30005, 3, '武汉智慧园区公司', '智慧园区', 'enterprise', '智慧园区', 'medium', 'cold_call', 3023, 'potential', '027-87000005', 'contact@park.demo', '武汉市光谷', '待技术澄清', 3001),
(30006, 3, '厦门跨境电商平台', '跨境平台', 'enterprise', '跨境电商', 'medium', 'website', 3028, 'active', '0592-55000006', 'cto@cross.demo', '厦门市湖里区', '运维支持客户', 3001),
(40001, 4, '青藤少儿编程中心', '青藤编程', 'enterprise', '少儿编程', 'medium', 'website', 4021, 'active', '0571-82000001', 'admin@qingteng.demo', '杭州市拱墅区', '课程合作校区', 4001),
(40002, 4, '明德企业培训学院', '明德学院', 'enterprise', '企业培训', 'large', 'referral', 4022, 'active', '021-62000002', 'hr@mingde.demo', '上海市静安区', '企业内训客户', 4001),
(40003, 4, '星火职业技能学校', '星火职校', 'enterprise', '职业教育', 'medium', 'exhibition', 4023, 'potential', '025-86000003', 'contact@xinghuo.demo', '南京市秦淮区', '招生合作洽谈中', 4001),
(40004, 4, '未来领航研学机构', '未来研学', 'enterprise', '研学教育', 'small', 'website', 4024, 'active', '010-65000004', 'ops@futureedu.demo', '北京市海淀区', '研学课程交付中', 4001),
(40005, 4, '南山语言培训中心', '南山语言', 'enterprise', '语言培训', 'small', 'cold_call', 4025, 'potential', '0755-83000005', 'info@nanshan.demo', '深圳市南山区', '待试听安排', 4001),
(40006, 4, '启明星在线教育平台', '启明星在线', 'enterprise', '在线教育', 'medium', 'referral', 4026, 'active', '020-86000006', 'service@qmx.demo', '广州市越秀区', '课程运营合作', 4001);

INSERT IGNORE INTO `biz_contact`
(`id`, `tenant_id`, `customer_id`, `name`, `position`, `phone`, `email`, `is_primary`) VALUES
(20001, 2, 20001, '赵敏', '采购总监', '13920000001', 'zhaomin@yunmao.demo', 1),
(20002, 2, 20002, '钱强', '渠道负责人', '13920000002', 'qianqiang@szzhizao.demo', 1),
(20003, 2, 20003, '孙悦', '运营总监', '13920000003', 'sunyue@baichuan.demo', 1),
(20004, 2, 20004, '李岩', '物流经理', '13920000004', 'liyan@yuncang.demo', 1),
(30001, 3, 30001, '王磊', '技术负责人', '13930000001', 'wanglei@starlink.demo', 1),
(30002, 3, 30002, '陈晨', '产品总监', '13930000002', 'chenchen@meddata.demo', 1),
(30003, 3, 30003, '刘洋', '研究员', '13930000003', 'liuyang@iov.demo', 1),
(30004, 3, 30004, '何静', '交付经理', '13930000004', 'hejing@iiot.demo', 1),
(40001, 4, 40001, '林老师', '校区负责人', '13940000001', 'lin@qingteng.demo', 1),
(40002, 4, 40002, '吴经理', '培训采购', '13940000002', 'wu@mingde.demo', 1),
(40003, 4, 40003, '郑主任', '教务主任', '13940000003', 'zheng@xinghuo.demo', 1),
(40004, 4, 40004, '黄老师', '课程负责人', '13940000004', 'huang@futureedu.demo', 1);

INSERT IGNORE INTO `biz_follow_up`
(`id`, `tenant_id`, `customer_id`, `user_id`, `type`, `content`, `ai_summary`, `next_plan`, `next_time`, `create_time`) VALUES
(20001, 2, 20001, 2002, 'visit', '拜访云贸科技，确认年度 CRM 采购预算', '客户预算明确，关注交付周期', '提交报价单', '2026-05-22 10:00:00', '2026-05-19 10:00:00'),
(20002, 2, 20003, 2018, 'call', '电话沟通百川零售门店拓展计划', '客户仍在比价', '安排产品演示', '2026-05-23 14:00:00', '2026-05-18 15:00:00'),
(30001, 3, 30001, 3027, 'meeting', '星链金科需求评审会，确认权限模型', '权限模型需要细化到数据域', '输出原型和排期', '2026-05-24 09:30:00', '2026-05-19 11:00:00'),
(30002, 3, 30004, 3023, 'email', '发送工业互联网中心上线清单', '上线准备进入收尾', '周五确认灰度计划', '2026-05-23 16:00:00', '2026-05-18 09:00:00'),
(40001, 4, 40001, 4021, 'visit', '青藤编程校区课程合作洽谈', '对方希望引入 AI 编程课程', '安排试听课', '2026-05-25 10:30:00', '2026-05-19 13:30:00'),
(40002, 4, 40002, 4022, 'meeting', '明德企业培训内训需求访谈', '重点是管理者沟通课程', '提交课程大纲', '2026-05-24 15:00:00', '2026-05-18 16:00:00');

-- ======================== 6. 合同与里程碑 ========================
INSERT IGNORE INTO `biz_contract`
(`id`, `tenant_id`, `contract_no`, `title`, `customer_id`, `type`, `amount`, `currency`, `sign_date`, `start_date`, `end_date`, `status`, `owner_id`, `ai_risk_score`, `ai_risk_note`, `remark`, `create_by`) VALUES
(20001, 2, 'SALES-2026-001', '云贸科技年度销售服务合同', 20001, 'service', 680000.00, 'CNY', '2026-04-15', '2026-05-01', '2027-04-30', 'active', 2002, 18, '低风险，回款节点清晰', '销售服务主合同', 2001),
(20002, 2, 'SALES-2026-002', '苏州智造渠道拓展合同', 20002, 'sales', 520000.00, 'CNY', '2026-05-01', '2026-05-10', '2026-12-31', 'active', 2010, 24, '需关注验收周期', '渠道项目', 2001),
(20003, 2, 'SALES-2026-003', '百川零售客户成功服务合同', 20003, 'service', 260000.00, 'CNY', NULL, '2026-06-01', '2026-11-30', 'pending', 2023, 36, '合同仍待法务确认', '待签署', 2001),
(30001, 3, 'DEV-2026-001', '星链金科风控平台研发合同', 30001, 'service', 1800000.00, 'CNY', '2026-03-20', '2026-04-01', '2026-12-31', 'active', 3027, 28, '数据安全条款需持续跟踪', '定制研发', 3001),
(30002, 3, 'DEV-2026-002', '数智医疗 SaaS 平台合同', 30002, 'service', 960000.00, 'CNY', '2026-04-08', '2026-04-15', '2026-10-31', 'active', 3002, 22, '需求范围已冻结', '医疗信息化项目', 3001),
(30003, 3, 'DEV-2026-003', '智慧园区运维支持合同', 30005, 'service', 300000.00, 'CNY', NULL, '2026-06-01', '2027-05-31', 'draft', 3023, 40, '报价和 SLA 待确认', '运维支持', 3001),
(40001, 4, 'EDU-2026-001', '青藤编程 AI 课程合作合同', 40001, 'service', 360000.00, 'CNY', '2026-04-25', '2026-05-01', '2026-12-31', 'active', 4021, 15, '课程交付可控', '课程合作', 4001),
(40002, 4, 'EDU-2026-002', '明德学院企业内训合同', 40002, 'service', 480000.00, 'CNY', '2026-05-06', '2026-05-15', '2026-09-30', 'active', 4022, 20, '讲师排期需锁定', '企业培训', 4001),
(40003, 4, 'EDU-2026-003', '星火职校招生合作协议', 40003, 'sales', 180000.00, 'CNY', NULL, '2026-06-01', '2026-08-31', 'pending', 4023, 34, '招生指标需确认', '合作协议', 4001);

INSERT IGNORE INTO `biz_contract_milestone`
(`id`, `tenant_id`, `contract_id`, `name`, `amount`, `due_date`, `status`) VALUES
(20001, 2, 20001, '首付款', 204000.00, '2026-05-15', 'completed'),
(20002, 2, 20001, '季度验收', 238000.00, '2026-08-31', 'pending'),
(20003, 2, 20002, '渠道启动', 156000.00, '2026-06-10', 'pending'),
(30001, 3, 30001, '原型验收', 360000.00, '2026-05-30', 'pending'),
(30002, 3, 30001, '开发交付', 900000.00, '2026-09-30', 'pending'),
(30003, 3, 30002, 'SaaS 上线', 480000.00, '2026-08-31', 'pending'),
(40001, 4, 40001, '课程试点', 108000.00, '2026-06-15', 'pending'),
(40002, 4, 40001, '批量开课', 180000.00, '2026-09-30', 'pending'),
(40003, 4, 40002, '第一期内训', 240000.00, '2026-07-15', 'pending');

-- ======================== 7. 工单 ========================
INSERT IGNORE INTO `biz_work_order`
(`id`, `tenant_id`, `order_no`, `title`, `type`, `priority`, `status`, `customer_id`, `reporter_id`, `assignee_id`, `dept_id`, `description`, `ai_category`, `ai_suggestion`, `sla_deadline`, `create_time`) VALUES
(20001, 2, 'SA-WO-20260519-001', '云贸客户报价审批加急', 'support', 'high', 'assigned', 20001, 2002, 2030, 207, '客户要求当天确认折扣权限', '商务支持', '建议走快速折扣审批流程', '2026-05-20 18:00:00', '2026-05-19 09:00:00'),
(20002, 2, 'SA-WO-20260519-002', '百川零售演示环境申请', 'feature', 'medium', 'open', 20003, 2018, 2027, 206, '需要准备行业演示数据', '售前演示', '建议使用零售行业模板', '2026-05-23 18:00:00', '2026-05-19 10:00:00'),
(20003, 2, 'SA-WO-20260519-003', '客户成功回访投诉处理', 'complaint', 'urgent', 'in_progress', 20006, 2024, 2023, 205, '新锐消费反馈响应慢', '客户投诉', '优先安排客户成功经理回访', '2026-05-20 12:00:00', '2026-05-19 11:00:00'),
(30001, 3, 'DEV-WO-20260519-001', '星链金科登录接口异常', 'bug', 'urgent', 'in_progress', 30001, 3027, 3011, 304, '测试环境偶发 502', '后端故障', '检查网关和服务注册状态', '2026-05-20 10:00:00', '2026-05-19 09:30:00'),
(30002, 3, 'DEV-WO-20260519-002', '医疗平台新增审计字段', 'feature', 'medium', 'assigned', 30002, 3002, 3006, 303, '客户要求页面展示审计字段', '功能需求', '先补字段展示再联调接口', '2026-05-24 18:00:00', '2026-05-19 12:00:00'),
(30003, 3, 'DEV-WO-20260519-003', '工业互联网部署巡检', 'maintenance', 'high', 'open', 30004, 3023, 3024, 306, '上线前检查 Redis、Qdrant、MinIO', '运维巡检', '按部署清单逐项确认', '2026-05-22 18:00:00', '2026-05-19 15:00:00'),
(40001, 4, 'EDU-WO-20260519-001', '青藤编程试听课排班', 'support', 'medium', 'assigned', 40001, 4021, 4007, 403, '周末试听课需要 2 名讲师', '课程排班', '优先安排讲师一部', '2026-05-22 18:00:00', '2026-05-19 08:30:00'),
(40002, 4, 'EDU-WO-20260519-002', '企业内训课件更新', 'feature', 'high', 'in_progress', 40002, 4022, 4002, 402, '明德学院需要更新沟通课程案例', '教研需求', '补充行业案例和课后作业', '2026-05-23 18:00:00', '2026-05-19 13:00:00'),
(40003, 4, 'EDU-WO-20260519-003', '招生咨询线索导入', 'support', 'medium', 'open', 40003, 4023, 4026, 406, '星火职校咨询名单需要批量导入', '教务运营', '清洗表格后导入 CRM', '2026-05-24 18:00:00', '2026-05-19 16:00:00');

INSERT IGNORE INTO `biz_work_order_comment`
(`id`, `order_id`, `user_id`, `content`, `is_internal`, `create_time`) VALUES
(20001, 20001, 2030, '已收到折扣申请，正在核对合同金额。', 1, '2026-05-19 10:00:00'),
(30001, 30001, 3011, '已定位到网关健康检查配置，准备修复。', 1, '2026-05-19 11:00:00'),
(40001, 40002, 4002, '课件案例已更新 60%，今晚提交初稿。', 1, '2026-05-19 17:00:00');

-- ======================== 8. 工作派发 ========================
INSERT IGNORE INTO `biz_dispatch`
(`id`, `tenant_id`, `title`, `description`, `type`, `priority`, `dispatcher_id`, `dept_id`, `due_date`, `status`, `biz_type`, `biz_id`, `create_time`) VALUES
(20001, 2, '整理本周重点客户清单', '销售一部和二部提交 TOP20 客户推进状态', 'task', 'high', 2001, 202, '2026-05-23 18:00:00', 'in_progress', 'customer', 20001, '2026-05-19 09:00:00'),
(20002, 2, '准备百川零售演示脚本', '市场部和销售二部联合准备演示材料', 'task', 'medium', 2001, 206, '2026-05-24 18:00:00', 'pending', 'customer', 20003, '2026-05-19 10:30:00'),
(30001, 3, '完成星链金科接口联调', '后端组、测试部共同完成登录接口验证', 'project', 'urgent', 3001, 304, '2026-05-21 18:00:00', 'accepted', 'work_order', 30001, '2026-05-19 09:30:00'),
(30002, 3, '输出医疗 SaaS 原型评审材料', '产品部准备评审文档，前端补交互原型', 'task', 'high', 3001, 302, '2026-05-23 18:00:00', 'in_progress', 'contract', 30002, '2026-05-19 11:00:00'),
(40001, 4, '安排青藤编程试听课', '讲师一部完成试听课排班和课件检查', 'task', 'high', 4001, 403, '2026-05-22 18:00:00', 'accepted', 'work_order', 40001, '2026-05-19 09:00:00'),
(40002, 4, '提交明德内训课程大纲', '教研部在周五前提交课程大纲', 'task', 'medium', 4001, 402, '2026-05-24 18:00:00', 'pending', 'contract', 40002, '2026-05-19 14:00:00');

INSERT IGNORE INTO `biz_dispatch_assignee`
(`id`, `dispatch_id`, `user_id`, `status`, `progress`, `note`, `completed_at`) VALUES
(20001, 20001, 2002, 'accepted', 40, '销售一部已汇总一半客户', NULL),
(20002, 20001, 2010, 'accepted', 35, '销售二部正在更新商机阶段', NULL),
(20003, 20002, 2027, 'pending', 0, NULL, NULL),
(30001, 30001, 3011, 'accepted', 60, '后端接口修复中', NULL),
(30002, 30001, 3018, 'accepted', 30, '测试用例已补充', NULL),
(30003, 30002, 3002, 'accepted', 70, '评审材料初稿已完成', NULL),
(40001, 40001, 4007, 'accepted', 50, '讲师已确认档期', NULL),
(40002, 40002, 4002, 'pending', 0, NULL, NULL);

-- ======================== 9. 日程、提醒、通知 ========================
INSERT IGNORE INTO `biz_schedule`
(`id`, `tenant_id`, `user_id`, `title`, `description`, `type`, `start_time`, `end_time`, `all_day`, `location`, `biz_type`, `biz_id`, `recurrence`, `color`, `status`, `create_time`) VALUES
(20001, 2, 2001, '销售周会', '销售、渠道、客服同步重点客户进展', 'meeting', '2026-05-20 09:00:00', '2026-05-20 10:30:00', 0, '星河 8 楼会议室', NULL, NULL, 'weekly', '#ef4444', 'active', '2026-05-19 08:00:00'),
(20002, 2, 2002, '拜访云贸科技', '携带报价方案和实施计划', 'follow_up', '2026-05-22 10:00:00', '2026-05-22 12:00:00', 0, '云贸科技总部', 'customer', 20001, NULL, '#f59e0b', 'active', '2026-05-19 09:00:00'),
(20003, 2, 2023, '客户成功复盘', '复盘新锐消费响应慢问题', 'meeting', '2026-05-21 15:00:00', '2026-05-21 16:00:00', 0, '线上会议', 'work_order', 20003, NULL, '#3b82f6', 'active', '2026-05-19 11:30:00'),
(30001, 3, 3001, '研发管理周会', '产品、研发、测试、运维项目同步', 'meeting', '2026-05-20 10:00:00', '2026-05-20 11:30:00', 0, '云启 5 楼大会议室', NULL, NULL, 'weekly', '#6366f1', 'active', '2026-05-19 08:00:00'),
(30002, 3, 3011, '星链金科接口联调', '修复登录接口 502 并完成回归', 'event', '2026-05-20 14:00:00', '2026-05-20 18:00:00', 0, '研发作战室', 'work_order', 30001, NULL, '#ef4444', 'active', '2026-05-19 09:30:00'),
(30003, 3, 3002, '医疗 SaaS 原型评审', '产品部组织客户原型评审', 'meeting', '2026-05-23 09:30:00', '2026-05-23 11:00:00', 0, '腾讯会议', 'contract', 30002, NULL, '#10b981', 'active', '2026-05-19 12:00:00'),
(40001, 4, 4001, '教学运营例会', '教研、讲师、招生咨询同步本周计划', 'meeting', '2026-05-20 09:30:00', '2026-05-20 11:00:00', 0, '知行 3 楼会议室', NULL, NULL, 'weekly', '#8b5cf6', 'active', '2026-05-19 08:00:00'),
(40002, 4, 4007, '青藤编程试听课', '试听课演示和家长答疑', 'event', '2026-05-24 14:00:00', '2026-05-24 16:00:00', 0, '青藤编程校区', 'customer', 40001, NULL, '#f59e0b', 'active', '2026-05-19 09:00:00'),
(40003, 4, 4002, '明德内训课件评审', '教研部评审企业内训课件', 'meeting', '2026-05-22 15:00:00', '2026-05-22 17:00:00', 0, '知行 2 楼教研室', 'work_order', 40002, NULL, '#3b82f6', 'active', '2026-05-19 13:00:00');

INSERT IGNORE INTO `biz_schedule_participant`
(`id`, `schedule_id`, `user_id`, `response`) VALUES
(20001, 20001, 2002, 'accepted'),
(20002, 20001, 2010, 'accepted'),
(20003, 20001, 2023, 'accepted'),
(30001, 30001, 3002, 'accepted'),
(30002, 30001, 3011, 'accepted'),
(30003, 30001, 3018, 'accepted'),
(40001, 40001, 4002, 'accepted'),
(40002, 40001, 4007, 'accepted'),
(40003, 40001, 4021, 'accepted');

INSERT IGNORE INTO `biz_task_reminder`
(`id`, `tenant_id`, `user_id`, `title`, `content`, `remind_time`, `remind_type`, `channel`, `biz_type`, `biz_id`, `status`) VALUES
(20001, 2, 2002, '提交云贸科技报价', '请在拜访前提交最终报价单', '2026-05-21 18:00:00', 'once', 'web', 'customer', 20001, 'pending'),
(30001, 3, 3011, '星链接口联调截止', '请同步接口修复进展', '2026-05-20 17:00:00', 'once', 'web', 'work_order', 30001, 'pending'),
(40001, 4, 4007, '试听课准备', '确认试听课设备和课件', '2026-05-24 10:00:00', 'once', 'web', 'schedule', 40002, 'pending');

INSERT IGNORE INTO `sys_notification`
(`id`, `tenant_id`, `user_id`, `type`, `title`, `content`, `biz_type`, `biz_id`, `is_read`, `channel`, `send_status`, `create_time`) VALUES
(20001, 2, 2002, 'system', '销售周会提醒', '明天 09:00 召开销售周会', 'schedule', 20001, 0, 'web', 'sent', '2026-05-19 18:00:00'),
(20002, 2, 2030, 'mention', '折扣审批待处理', '云贸客户报价审批需要财务行政确认', 'work_order', 20001, 0, 'web', 'sent', '2026-05-19 10:05:00'),
(30001, 3, 3011, 'alert', '紧急工单已分配', '星链金科登录接口异常已分配给你', 'work_order', 30001, 0, 'web', 'sent', '2026-05-19 09:35:00'),
(30002, 3, 3002, 'approval', '原型评审准备', '医疗 SaaS 原型评审材料请按时提交', 'schedule', 30003, 0, 'web', 'sent', '2026-05-19 12:05:00'),
(40001, 4, 4007, 'system', '试听课任务提醒', '青藤编程试听课已安排，请确认讲师排班', 'schedule', 40002, 0, 'web', 'sent', '2026-05-19 09:10:00'),
(40002, 4, 4002, 'mention', '课件更新待完成', '明德内训课件需要今晚提交初稿', 'work_order', 40002, 0, 'web', 'sent', '2026-05-19 17:05:00');

DROP TEMPORARY TABLE IF EXISTS `tmp_v21_seq`;
SET FOREIGN_KEY_CHECKS = 1;

-- ======================== 验证查询 ========================
SELECT 'V2.1 tenant test data loaded' AS info;
SELECT `tenant_id`, COUNT(*) AS user_count
FROM `sys_user`
WHERE `tenant_id` IN (2, 3, 4) AND `deleted` = 0
GROUP BY `tenant_id`
ORDER BY `tenant_id`;
SELECT t.`id` AS tenant_id, t.`name`, COUNT(u.`id`) AS company_boss_count
FROM `sys_tenant` t
LEFT JOIN `sys_role` r ON r.`tenant_id` = t.`id` AND r.`role_code` = 'SUPER_ADMIN' AND r.`deleted` = 0
LEFT JOIN `sys_user_role` ur ON ur.`tenant_id` = t.`id` AND ur.`role_id` = r.`id`
LEFT JOIN `sys_user` u ON u.`tenant_id` = t.`id` AND u.`id` = ur.`user_id` AND u.`deleted` = 0
WHERE t.`id` IN (2, 3, 4)
GROUP BY t.`id`, t.`name`
ORDER BY t.`id`;
