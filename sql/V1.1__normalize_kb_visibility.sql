-- =====================================================================
-- V1.1 · 知识库可见性规范化
-- ---------------------------------------------------------------------
-- 背景：
--   schema 里 knowledge_base.visibility 注释允许 PUBLIC / DEPT / ROLE / USER / PRIVATE 五种值，
--   但应用层始终没有实现 DEPT / ROLE / USER 这三种"半公开"对应的 ACL 校验逻辑——
--   旧代码用反向（黑名单）判定 isPrivate = (null || 'PRIVATE')，导致这三种值被
--   事实上当作 PUBLIC 放行；新代码改成白名单 isPublic = ('PUBLIC')，会让历史上
--   用了这三种值的 KB 突然变成 owner-only 不可读。
--
-- 决策（产品确认）：
--   现有 DEPT / ROLE / USER 的 KB 与"事实公开"的体验一致，统一升级到 PUBLIC，
--   保留旧用户的访问能力；后续如果需要真正的部门 / 角色 / 用户级 ACL，再单独
--   引入 knowledge_base_acl 表的查询逻辑并开新的可见性枚举。
--
-- 安全：
--   - 仅修改非 PRIVATE / 非 PUBLIC 的行；PRIVATE 和 PUBLIC 不受影响
--   - 幂等：重复执行不会改写已经规范化过的数据
--   - 不删除任何记录
-- =====================================================================
USE `zwskchat`;

-- 1. 先做一次 dry-run 统计（执行前后对比可看效果；select 不改数据）
SELECT
    visibility                           AS `legacy_visibility`,
    COUNT(*)                             AS `kb_count`,
    GROUP_CONCAT(id ORDER BY id)         AS `kb_ids`
FROM `knowledge_base`
WHERE deleted = 0
  AND visibility IS NOT NULL
  AND UPPER(visibility) NOT IN ('PRIVATE', 'PUBLIC')
GROUP BY visibility;

-- 2. 规范化升级：把所有非 PRIVATE / 非 PUBLIC 的可见性统一升到 'PUBLIC'
--    覆盖大小写差异和首尾空白（'public ' / 'Dept' / ' role ' 等历史脏数据）。
UPDATE `knowledge_base`
SET    `visibility`  = 'PUBLIC',
       `update_time` = CURRENT_TIMESTAMP
WHERE  deleted = 0
  AND  visibility IS NOT NULL
  AND  UPPER(TRIM(visibility)) NOT IN ('PRIVATE', 'PUBLIC');

-- 3. 顺手把 visibility 为 NULL 的脏数据兜底到 'PRIVATE'（schema 默认就是 PRIVATE，
--    NOT NULL 约束本应不允许 NULL，但历史导入数据可能绕过约束）
UPDATE `knowledge_base`
SET    `visibility`  = 'PRIVATE',
       `update_time` = CURRENT_TIMESTAMP
WHERE  deleted = 0
  AND  visibility IS NULL;

-- 4. 把所有现存 visibility 大小写统一成大写（防御性，应用层用 equalsIgnoreCase 容忍，
--    但 DB 层规整后查询和 GROUP BY 更直观）
UPDATE `knowledge_base`
SET    `visibility` = UPPER(TRIM(visibility))
WHERE  deleted = 0
  AND  visibility IS NOT NULL
  AND  visibility <> UPPER(TRIM(visibility));

-- 5. 执行后再次统计，应只剩 PRIVATE / PUBLIC 两种值
SELECT
    visibility                           AS `current_visibility`,
    COUNT(*)                             AS `kb_count`
FROM `knowledge_base`
WHERE deleted = 0
GROUP BY visibility
ORDER BY visibility;
