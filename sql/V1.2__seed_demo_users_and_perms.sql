-- =====================================================================
-- V1.2 · 演示用户 + 权限关联补全
-- ---------------------------------------------------------------------
-- 做三件事：
--   1) 给所有内置角色铺设权限（之前 sys_role_permission 是空的，所以登录后 perms=[]）
--   2) 插入若干演示用户（密码由部署环境另行设置）
--   3) 为他们绑定对应角色
--
-- 幂等：用 INSERT IGNORE，重复执行不会报主键冲突；权限码 (perm_code) 唯一约束保证不重复
-- ---------------------------------------------------------------------
USE `zwskchat`;

-- ==================== 1. 系统权限定义 ====================
-- perm_code 命名约定：模块:对象:动作（与代码里 @PreAuthorize 表达式对齐）
-- 类型：menu（前端菜单）/ api（接口）/ data（数据范围）
INSERT IGNORE INTO `sys_permission`(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`) VALUES
    -- 知识库
    (1001, 0, 'kb:menu',         '知识库管理',     'menu', '/kb',                   NULL,    10, 1),
    (1002, 1001, 'kb:list',      '查看知识库列表', 'api',  '/kb',                   'GET',   11, 1),
    (1003, 1001, 'kb:create',    '创建知识库',     'api',  '/kb',                   'POST',  12, 1),
    (1004, 1001, 'kb:delete',    '删除知识库',     'api',  '/kb/*',                 'DELETE',13, 1),
    (1005, 1001, 'kb:doc:upload','上传文档',       'api',  '/kb/*/documents/**',    'POST',  14, 1),
    (1006, 1001, 'kb:doc:delete','删除文档',       'api',  '/kb/*/documents/*',     'DELETE',15, 1),
    -- 聊天 / 会话
    (2001, 0, 'chat:menu',       '智能问答',       'menu', '/chat',                 NULL,    20, 1),
    (2002, 2001, 'chat:create',  '创建会话',       'api',  '/chat/sessions',        'POST',  21, 1),
    (2003, 2001, 'chat:stream',  '流式问答',       'api',  '/chat/sessions/*/completions/**', 'POST', 22, 1),
    -- 语音
    (3001, 0, 'voice:menu',      '语音对话',       'menu', '/voice-ai',             NULL,    30, 1),
    (3002, 3001, 'voice:use',    '使用语音助手',   'api',  '/voice/ws/**',          NULL,    31, 1),
    -- 用户 / 系统管理（仅 admin 类角色可见）
    (4001, 0, 'sys:menu',        '系统管理',       'menu', '/sys',                  NULL,    90, 1),
    (4002, 4001, 'sys:user:view','查看用户',       'api',  '/users/**',             'GET',   91, 1),
    (4003, 4001, 'sys:user:edit','修改用户',       'api',  '/users/**',             'POST',  92, 1),
    (4004, 4001, 'sys:audit:view','查看审计日志',  'api',  '/audit/**',             'GET',   93, 1);

-- ==================== 2. 角色 ↔ 权限关联 ====================
-- SUPER_ADMIN (id=1)：全部权限
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (1000000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p
WHERE p.id IN (1001,1002,1003,1004,1005,1006,
               2001,2002,2003,
               3001,3002,
               4001,4002,4003,4004);

-- TENANT_ADMIN (id=2)：除审计外的所有功能
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (2000001, 2, 1001),(2000002, 2, 1002),(2000003, 2, 1003),(2000004, 2, 1004),(2000005, 2, 1005),(2000006, 2, 1006),
    (2000007, 2, 2001),(2000008, 2, 2002),(2000009, 2, 2003),
    (2000010, 2, 3001),(2000011, 2, 3002),
    (2000012, 2, 4001),(2000013, 2, 4002),(2000014, 2, 4003);

-- KB_ADMIN (id=3)：完整 KB 权限 + 聊天 / 语音
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (3000001, 3, 1001),(3000002, 3, 1002),(3000003, 3, 1003),(3000004, 3, 1004),(3000005, 3, 1005),(3000006, 3, 1006),
    (3000007, 3, 2001),(3000008, 3, 2002),(3000009, 3, 2003),
    (3000010, 3, 3001),(3000011, 3, 3002);

-- STAFF (id=4)：只读 KB + 聊天 / 语音
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (4000001, 4, 1001),(4000002, 4, 1002),
    (4000003, 4, 2001),(4000004, 4, 2002),(4000005, 4, 2003),
    (4000006, 4, 3001),(4000007, 4, 3002);

-- AUDITOR (id=5)：仅查看审计日志 + 看会话历史
INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`) VALUES
    (5000001, 5, 4001),(5000002, 5, 4004),
    (5000003, 5, 2001);

-- ==================== 3. 演示用户 ====================
-- 源码不提供演示用户默认密码；以下为不可直接登录的占位 hash。
INSERT IGNORE INTO `sys_user`
(`id`, `tenant_id`, `username`, `password`, `nickname`, `email`, `status`)
VALUES
    (10, 1, 'kb_admin',    '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '知识库管理员', 'kb_admin@zwsk.demo',    1),
    (11, 1, 'tenant_boss', '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '企业管理员',   'boss@zwsk.demo',        1),
    (12, 1, 'auditor',     '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '审计员',       'audit@zwsk.demo',       1),
    (13, 1, 'alice',       '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', 'Alice',        'alice@zwsk.demo',       1),
    (14, 1, 'bob',         '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', 'Bob',          'bob@zwsk.demo',         1),
    (15, 1, 'carol',       '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', 'Carol',        'carol@zwsk.demo',       1),
    (16, 1, 'demo_locked', '$2a$10$7s7q3VqYx3z3p3xgkN4WyOe7DfM8yKmN8r6Ej2sJjN3kZxX9p8w7e', '已停用账号',   NULL,                    0);

-- ==================== 4. 用户 ↔ 角色绑定 ====================
INSERT IGNORE INTO `sys_user_role`(`id`, `tenant_id`, `user_id`, `role_id`) VALUES
    (10, 1, 10, 3),  -- kb_admin     → KB_ADMIN
    (11, 1, 11, 2),  -- tenant_boss  → TENANT_ADMIN
    (12, 1, 12, 5),  -- auditor      → AUDITOR
    (13, 1, 13, 4),  -- alice        → STAFF
    (14, 1, 14, 4),  -- bob          → STAFF
    (15, 1, 15, 4),  -- carol        → STAFF
    (16, 1, 16, 4);  -- demo_locked  → STAFF（账号已停用，仅展示锁定流程）

-- ==================== 5. 验证结果 ====================
-- 期望：用户列表含 admin 在内 8 个；每个内置角色都至少有 1 条 sys_role_permission
SELECT u.id, u.username, u.nickname, u.status,
       GROUP_CONCAT(r.role_code ORDER BY r.id SEPARATOR ',') AS roles
FROM `sys_user` u
LEFT JOIN `sys_user_role` ur ON ur.user_id = u.id
LEFT JOIN `sys_role` r ON r.id = ur.role_id AND r.deleted = 0
WHERE u.deleted = 0 AND u.tenant_id = 1
GROUP BY u.id, u.username, u.nickname, u.status
ORDER BY u.id;

SELECT r.role_code, COUNT(rp.permission_id) AS perm_count
FROM `sys_role` r
LEFT JOIN `sys_role_permission` rp ON rp.role_id = r.id
WHERE r.deleted = 0
GROUP BY r.id, r.role_code
ORDER BY r.id;
