-- Tenant management permissions and tenant administrator hardening.
USE `zwskchat`;

INSERT IGNORE INTO `sys_permission`
(`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `path`, `method`, `sort`, `status`) VALUES
    (4010, 4001, 'tenant:menu', '租户管理', 'menu', '/tenants', NULL, 94, 1),
    (4011, 4010, 'tenant:view', '查看租户', 'api', '/tenants/**', 'GET', 95, 1),
    (4012, 4010, 'tenant:edit', '管理租户', 'api', '/tenants/**', 'POST', 96, 1),
    (4013, 4010, 'tenant:user:view', '查看租户用户', 'api', '/tenants/*/users/**', 'GET', 97, 1),
    (4014, 4010, 'tenant:user:edit', '管理租户用户', 'api', '/tenants/*/users/**', 'POST', 98, 1);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (1100000 + p.id) AS id, 1 AS role_id, p.id AS permission_id
FROM `sys_permission` p
WHERE p.id IN (4010, 4011, 4012, 4013, 4014);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT (1200000 + p.id) AS id, 2 AS role_id, p.id AS permission_id
FROM `sys_permission` p
WHERE p.id IN (4010, 4013, 4014);

INSERT IGNORE INTO `sys_role_permission`(`id`, `role_id`, `permission_id`)
SELECT CAST(CONV(SUBSTR(MD5(CONCAT(r.id, ':', p.id)), 1, 15), 16, 10) AS UNSIGNED) AS id,
       r.id AS role_id,
       p.id AS permission_id
FROM `sys_role` r
JOIN `sys_permission` p ON p.id IN (4010, 4013, 4014)
WHERE r.role_code = 'TENANT_ADMIN'
  AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM `sys_role_permission` rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
