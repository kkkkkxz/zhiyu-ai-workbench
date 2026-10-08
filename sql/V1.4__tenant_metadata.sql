-- Tenant metadata for production operation.
-- Keeps existing tenant IDs stable and only adds optional management fields.

ALTER TABLE `sys_tenant`
    ADD COLUMN `plan_code` VARCHAR(64) NOT NULL DEFAULT 'standard' COMMENT 'Tenant plan code' AFTER `status`,
    ADD COLUMN `max_users` INT NOT NULL DEFAULT 100 COMMENT 'Maximum active users allowed' AFTER `plan_code`,
    ADD COLUMN `max_storage_mb` BIGINT NOT NULL DEFAULT 10240 COMMENT 'Maximum object storage in MB' AFTER `max_users`,
    ADD COLUMN `expires_at` DATETIME DEFAULT NULL COMMENT 'Tenant expiration time; null means no expiry' AFTER `max_storage_mb`,
    ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Tenant operation note' AFTER `expires_at`;

CREATE INDEX `idx_status_expiry` ON `sys_tenant` (`status`, `expires_at`);
