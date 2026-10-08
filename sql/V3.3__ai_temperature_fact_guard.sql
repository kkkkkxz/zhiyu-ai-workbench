ALTER TABLE tenant_ai_config
    MODIFY COLUMN `temperature` DECIMAL(4,2) NOT NULL DEFAULT 0.10;

UPDATE tenant_ai_config
SET `temperature` = 0.10
WHERE `temperature` > 0.15;
