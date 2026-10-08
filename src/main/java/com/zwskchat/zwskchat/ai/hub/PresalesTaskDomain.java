package com.zwskchat.zwskchat.ai.hub;

/**
 * 售前四大任务域（对应工程计划书“把系统能力拆成四个主任务域：客户需求匹配、案例检索、
 * 资料查询、简单方案草拟”）。
 *
 * <p>该枚举用于在 RAG 路径内进一步识别用户的售前意图，从而决定是否启用文档约定的
 * “结构化输出契约”（需求判断 / 匹配方案 / 可复用案例 / 风险与边界 / 建议下一步动作 / 依据来源）。
 * 与 {@link IntentType}（RAG / DATA_QUERY / BIZ_ACTION / CHART / GENERAL）是不同维度：
 * IntentType 决定走哪条主链路，PresalesTaskDomain 决定 RAG 链路内的回答组织方式。</p>
 */
public enum PresalesTaskDomain {

    /** 客户需求匹配：客户描述场景/诉求，系统判断并匹配方案、案例。 */
    NEED_MATCHING,

    /** 案例检索：客户/销售想找做过的类似项目、成功案例做背书。 */
    CASE_RETRIEVAL,

    /** 资料查询：查公司简介、资质、参数、报价、文档等确定性信息。 */
    MATERIAL_QUERY,

    /** 简单方案草拟：生成初版技术说明 / 建设建议 / 拜访纪要等。 */
    SOLUTION_DRAFTING,

    /** 与售前无关的通用对话。 */
    GENERAL;

    /**
     * 是否需要套用文档约定的结构化输出契约。
     * 需求匹配与方案草拟天然需要“需求判断→匹配方案→案例→风险→下一步→依据”的完整结构；
     * 案例检索同样受益于“匹配方案 + 可复用案例 + 下一步动作”的引导式输出。
     */
    public boolean requiresOutputContract() {
        return this == NEED_MATCHING || this == SOLUTION_DRAFTING || this == CASE_RETRIEVAL;
    }
}
