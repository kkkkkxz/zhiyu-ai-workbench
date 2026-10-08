package com.zwskchat.zwskchat.tenant.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TenantFeatureCatalog {

    public static final String WORKBENCH = "workbench";
    public static final String TEMPLATE_CENTER = "template_center";
    public static final String AI_CHAT = "ai_chat";
    public static final String AI_ACTIONS = "ai_actions";
    public static final String KNOWLEDGE_BASE = "knowledge_base";
    public static final String VOICE_AI = "voice_ai";
    public static final String VOICE_DIAGNOSTIC = "voice_diagnostic";
    public static final String CRM_CUSTOMERS = "crm_customers";
    public static final String CONTRACTS = "contracts";
    public static final String WORK_ORDERS = "work_orders";
    public static final String DISPATCH = "dispatch";
    public static final String SCHEDULE = "schedule";
    public static final String WORKFLOW = "workflow";
    public static final String NOTIFICATIONS = "notifications";
    public static final String TENANT_DASHBOARD = "tenant_dashboard";
    public static final String DEPARTMENTS = "departments";
    public static final String TENANT_CONSOLE = "tenant_console";

    private static final List<TenantFeatureDefinition> DEFINITIONS = List.of(
            feature(WORKBENCH, "工作台", "基础", "企业待办、快捷入口与核心概览", true, 10),
            feature(TEMPLATE_CENTER, "模板中心", "基础", "按行业/业务模板开通功能", true, 20),
            feature(AI_CHAT, "AI 对话", "AI 功能", "通用问答、RAG 知识问答与业务助手入口", true, 30),
            feature(AI_ACTIONS, "AI 动作中心", "AI 功能", "查看 AI 嵌入业务流程后的执行记录", true, 40),
            feature(KNOWLEDGE_BASE, "知识库", "AI 功能", "文档上传、解析、检索与权限控制", true, 50),
            feature(VOICE_AI, "语音助手", "AI 功能", "语音问答、ASR/TTS 与实时对话", false, 60),
            feature(VOICE_DIAGNOSTIC, "语音诊断", "AI 功能", "麦克风、设备和语音链路诊断", false, 70),
            feature(CRM_CUSTOMERS, "客户管理", "ERP 业务", "客户档案、联系人与跟进", false, 110),
            feature(CONTRACTS, "合同管理", "ERP 业务", "合同台账、附件、里程碑与 AI 风险识别", false, 120),
            feature(WORK_ORDERS, "工单管理", "ERP 业务", "客户报修、内部工单、附件判断与 AI 建议", false, 130),
            feature(DISPATCH, "工作派发", "ERP 业务", "任务派发、执行反馈与进度跟踪", false, 140),
            feature(SCHEDULE, "日程管理", "ERP 业务", "会议、提醒、跟进和任务日历", false, 150),
            feature(WORKFLOW, "审批管理", "ERP 业务", "合同、费用、任务等审批流转", false, 160),
            feature(NOTIFICATIONS, "通知中心", "ERP 业务", "待办、提醒与系统通知", false, 170),
            feature(TENANT_DASHBOARD, "数据看板", "经营分析", "当前公司业务数据与 AI 使用情况", false, 210),
            feature(DEPARTMENTS, "部门管理", "组织管理", "组织架构、上下级和部门维护", true, 310),
            feature(TENANT_CONSOLE, "公司控制台", "组织管理", "租户模块开关、AI Key 与公司配置", true, 320)
    );

    private static final Set<String> DEFAULT_ENABLED_CODES = Set.of(
            WORKBENCH,
            TEMPLATE_CENTER,
            AI_CHAT,
            AI_ACTIONS,
            KNOWLEDGE_BASE,
            DEPARTMENTS,
            TENANT_CONSOLE
    );

    private static final Map<String, List<String>> TEMPLATE_FEATURES = Map.of(
            "general-business", List.of(
                    CRM_CUSTOMERS,
                    CONTRACTS,
                    WORK_ORDERS,
                    DISPATCH,
                    SCHEDULE,
                    WORKFLOW,
                    NOTIFICATIONS,
                    TENANT_DASHBOARD,
                    AI_ACTIONS,
                    KNOWLEDGE_BASE
            ),
            "project-delivery", List.of(
                    CRM_CUSTOMERS,
                    CONTRACTS,
                    WORK_ORDERS,
                    DISPATCH,
                    SCHEDULE,
                    WORKFLOW,
                    NOTIFICATIONS,
                    TENANT_DASHBOARD,
                    AI_ACTIONS,
                    KNOWLEDGE_BASE
            ),
            "after-sales-service", List.of(
                    CRM_CUSTOMERS,
                    CONTRACTS,
                    WORK_ORDERS,
                    DISPATCH,
                    SCHEDULE,
                    NOTIFICATIONS,
                    TENANT_DASHBOARD,
                    AI_ACTIONS,
                    KNOWLEDGE_BASE,
                    VOICE_AI
            )
    );

    private TenantFeatureCatalog() {
    }

    public static List<TenantFeatureDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Set<String> defaultEnabledCodes() {
        return DEFAULT_ENABLED_CODES;
    }

    public static List<String> featureCodesForTemplate(String packageCode) {
        if (packageCode == null || packageCode.isBlank()) {
            return List.of();
        }
        return TEMPLATE_FEATURES.getOrDefault(packageCode.trim(), List.of());
    }

    private static TenantFeatureDefinition feature(String code,
                                                   String name,
                                                   String category,
                                                   String description,
                                                   boolean enabledByDefault,
                                                   int sort) {
        return new TenantFeatureDefinition(code, name, category, description, enabledByDefault, sort);
    }
}
