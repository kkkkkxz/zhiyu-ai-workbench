USE `zwskchat`;

ALTER TABLE `sys_dept`
    ADD COLUMN `code` VARCHAR(64) DEFAULT NULL AFTER `name`,
    ADD COLUMN `leader_user_id` BIGINT DEFAULT NULL AFTER `status`,
    ADD COLUMN `path` VARCHAR(500) DEFAULT NULL AFTER `leader_user_id`,
    ADD COLUMN `level` INT NOT NULL DEFAULT 1 AFTER `path`;

CREATE INDEX `idx_tenant_code` ON `sys_dept` (`tenant_id`, `code`);
CREATE INDEX `idx_tenant_path` ON `sys_dept` (`tenant_id`, `path`);
CREATE INDEX `idx_user_tenant_dept` ON `sys_user` (`tenant_id`, `dept_id`);

CREATE TABLE IF NOT EXISTS `sys_user_reporting` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `manager_user_id` BIGINT NOT NULL,
    `dept_id` BIGINT DEFAULT NULL,
    `relation_type` VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_manager` (`tenant_id`, `user_id`, `manager_user_id`, `deleted`),
    KEY `idx_manager` (`tenant_id`, `manager_user_id`),
    KEY `idx_user` (`tenant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户上下级关系';

CREATE TABLE IF NOT EXISTS `knowledge_document_acl` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `doc_id` BIGINT NOT NULL,
    `subject_type` VARCHAR(32) NOT NULL COMMENT 'USER/DEPT/REPORTING',
    `subject_id` BIGINT NOT NULL,
    `permission` VARCHAR(16) NOT NULL DEFAULT 'READ' COMMENT 'READ/MANAGE',
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_subject` (`doc_id`, `subject_type`, `subject_id`, `deleted`),
    KEY `idx_subject` (`tenant_id`, `subject_type`, `subject_id`),
    KEY `idx_doc` (`tenant_id`, `doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档查看权限';

CREATE TABLE IF NOT EXISTS `admin_apply` (
    `id` BIGINT NOT NULL,
    `tenant_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `apply_type` VARCHAR(32) NOT NULL DEFAULT 'ROLE',
    `target_role` VARCHAR(64) NOT NULL,
    `reason` VARCHAR(500) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `reviewer_id` BIGINT DEFAULT NULL,
    `review_time` DATETIME DEFAULT NULL,
    `reject_reason` VARCHAR(500) DEFAULT NULL,
    `create_by` BIGINT DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by` BIGINT DEFAULT NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_user_status` (`tenant_id`, `user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员申请审核';

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`) VALUES
    (4020, 4001, 'platform:dashboard', '数据看板', 'menu', '/platform/dashboard', NULL, 90, 1),
    (4021, 4001, 'tenant:dept:view', '查看部门', 'api', '/tenants/*/departments/**', 'GET', 99, 1),
    (4022, 4001, 'tenant:dept:edit', '管理部门', 'api', '/tenants/*/departments/**', 'POST', 100, 1),
    (4023, 4001, 'tenant:admin-apply:view', '查看管理员审核', 'api', '/admin-applies/**', 'GET', 101, 1),
    (4024, 4001, 'tenant:admin-apply:edit', '处理管理员审核', 'api', '/admin-applies/**', 'POST', 102, 1),
    (4025, 4001, 'kb:doc-acl:manage', '管理文档查看权限', 'api', '/kb/*/docs/*/acl', 'POST', 103, 1);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (1300000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p
WHERE p.id IN (4020, 4021, 4022, 4023, 4024, 4025);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
       r.id AS role_id,
       p.id AS permission_id
FROM `sys_role` r
JOIN `sys_permission` p ON p.id IN (4021, 4022, 4025)
WHERE r.role_code = 'TENANT_ADMIN'
  AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_role_permission` rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
