package com.zwskchat.zwskchat.ai.hub;

import com.zwskchat.zwskchat.security.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为 Text-to-SQL 提供当前租户可查询的业务表 schema 白名单。
 * <p>
 * 安全边界：
 * 1. 只暴露业务表，不暴露 sys_user/sys_role 等系统表
 * 2. 所有表 schema 均标注 tenant_id 列，TextToSqlEngine 执行前会强制校验 SQL 包含 tenant_id 条件
 * 3. 自动注入当前用户的 tenant_id 值，供 prompt 使用
 */
@Component
public class TenantSafeSchemaProvider {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "biz_customer",
            "biz_contract",
            "biz_contract_milestone",
            "biz_payment_reminder",
            "biz_work_order",
            "biz_dispatch",
            "biz_dispatch_assignee",
            "biz_schedule",
            "biz_follow_up",
            "sys_dept"
    );

    private static final Pattern TABLE_REF_PATTERN = Pattern.compile("\\b(?:from|join)\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern UNSAFE_SQL_PATTERN = Pattern.compile(
            "(?is)(;|--|/\\*|\\*/|#|\\bunion\\b|\\bor\\b|\\binto\\s+outfile\\b|\\bload_file\\s*\\(|\\bsleep\\s*\\(|\\bbenchmark\\s*\\()");

    /**
     * 业务表白名单 DDL 摘要（精简版，仅供 LLM 生成 SQL）
     */
    private static final String SCHEMA_TEMPLATE = """
            -- 客户表
            biz_customer(id BIGINT PK, tenant_id BIGINT, name VARCHAR, short_name VARCHAR, type VARCHAR, industry VARCHAR, phone VARCHAR, email VARCHAR, status VARCHAR, grade VARCHAR, ai_score INT, lifecycle_stage VARCHAR, owner_id BIGINT, create_time DATETIME)
            
            -- 合同表
            biz_contract(id BIGINT PK, tenant_id BIGINT, title VARCHAR, contract_no VARCHAR, customer_id BIGINT, type VARCHAR, amount DECIMAL, start_date DATE, end_date DATE, status VARCHAR, ai_risk_score INT, create_time DATETIME)
            
            -- 合同里程碑
            biz_contract_milestone(id BIGINT PK, tenant_id BIGINT, contract_id BIGINT, name VARCHAR, amount DECIMAL, due_date DATE, status VARCHAR)
            
            -- 回款提醒
            biz_payment_reminder(id BIGINT PK, tenant_id BIGINT, contract_id BIGINT, customer_id BIGINT, title VARCHAR, amount DECIMAL, due_date DATE, priority VARCHAR, status VARCHAR, create_time DATETIME)
            
            -- 工单表
            biz_work_order(id BIGINT PK, tenant_id BIGINT, order_no VARCHAR, title VARCHAR, type VARCHAR, priority VARCHAR, status VARCHAR, customer_id BIGINT, reporter_id BIGINT, assignee_id BIGINT, dept_id BIGINT, ai_category VARCHAR, create_time DATETIME, sla_deadline DATETIME)
            
            -- 派发任务
            biz_dispatch(id BIGINT PK, tenant_id BIGINT, title VARCHAR, description TEXT, type VARCHAR, priority VARCHAR, dispatcher_id BIGINT, dept_id BIGINT, due_date DATETIME, status VARCHAR, completion_note TEXT, completed_at DATETIME, biz_type VARCHAR, biz_id BIGINT, create_time DATETIME)
            
            -- 派发指派人（无 tenant_id；只能通过 biz_dispatch JOIN 后用 biz_dispatch.tenant_id 过滤）
            biz_dispatch_assignee(id BIGINT PK, dispatch_id BIGINT, user_id BIGINT, status VARCHAR, progress INT, note TEXT, completed_at DATETIME, create_time DATETIME)
            
            -- 日程
            biz_schedule(id BIGINT PK, tenant_id BIGINT, user_id BIGINT, title VARCHAR, type VARCHAR, start_time DATETIME, end_time DATETIME, status VARCHAR, create_time DATETIME)
            
            -- 跟进记录
            biz_follow_up(id BIGINT PK, tenant_id BIGINT, customer_id BIGINT, user_id BIGINT, type VARCHAR, content TEXT, create_time DATETIME)
            
            -- 部门（可用于 JOIN 统计）
            sys_dept(id BIGINT PK, tenant_id BIGINT, name VARCHAR, parent_id BIGINT, leader_user_id BIGINT)
            """;

    /**
     * 获取当前租户的 schema 信息（含 tenant_id 值提示）
     */
    public String getSchemaForCurrentTenant() {
        Long tenantId = SecurityUtils.currentUser().getTenantId();
        return SCHEMA_TEMPLATE + "\n-- 重要约束：所有查询必须包含当前租户条件 tenant_id = " + tenantId + "。JOIN 查询请使用主业务表或别名的 tenant_id 过滤，不允许跨租户查询。\n";
    }

    /**
     * 校验生成的 SQL 是否包含租户隔离条件
     */
    public boolean isTenantSafe(String sql, Long tenantId) {
        if (sql == null || tenantId == null) {
            return false;
        }
        String normalized = sql.toLowerCase()
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank() || UNSAFE_SQL_PATTERN.matcher(normalized).find()) {
            return false;
        }
        if (!containsOnlyAllowedTables(normalized)) {
            return false;
        }
        return normalized.matches("(?s).*\\b(?:[a-z0-9_]+\\.)?tenant_id\\s*=\\s*" + tenantId + "\\b.*");
    }

    private boolean containsOnlyAllowedTables(String normalized) {
        Matcher matcher = TABLE_REF_PATTERN.matcher(normalized);
        Set<String> referencedTables = new HashSet<>();
        while (matcher.find()) {
            String table = matcher.group(1);
            if (!ALLOWED_TABLES.contains(table)) {
                return false;
            }
            referencedTables.add(table);
        }
        if (referencedTables.isEmpty()) {
            return false;
        }
        return !referencedTables.contains("biz_dispatch_assignee") || referencedTables.contains("biz_dispatch");
    }
}
