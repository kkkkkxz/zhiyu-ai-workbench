package com.zwskchat.zwskchat.ai.hub;

/**
 * 售前四大任务域分类器（对应工程计划书“四个主任务域：客户需求匹配、案例检索、资料查询、简单方案草拟”）。
 *
 * <p>采用确定性关键词启发式而非额外的 LLM 调用，原因：
 * <ul>
 *   <li>RAG 主链路前已有一次 {@link IntentRouter} 的 LLM 意图识别，再加一次 LLM 子分类会显著增加延迟与成本；</li>
 *   <li>售前域分类只决定“回答如何组织”，对错分容忍度高（错分顶多是结构化程度不同，不会答错事实）；</li>
 *   <li>确定性规则可单测、可回归、零外部依赖，便于保证稳定无副作用。</li>
 * </ul>
 * 命中优先级：方案草拟 &gt; 客户需求匹配 &gt; 案例检索 &gt; 资料查询 &gt; 通用，
 * 越“重”的售前动作优先级越高，避免被泛化关键词抢占。</p>
 */
public final class PresalesIntentClassifier {

    private PresalesIntentClassifier() {
    }

    /** 简单方案草拟信号：要“产出物”。 */
    private static final String[] SOLUTION_DRAFTING = {
            "草拟", "出方案", "出个方案", "写方案", "写一版", "给一版", "给我一版", "初版",
            "方案设计", "设计方案", "技术方案", "解决方案", "建设建议", "建设方案",
            "拜访纪要", "技术说明", "怎么做", "如何落地", "怎么落地", "结合落地",
            "如何设计", "怎么设计", "如何优化", "怎么优化", "如何提升", "对比选型", "选型建议"
    };

    /** 客户需求匹配信号：客户自述场景 + 想要什么。 */
    private static final String[] NEED_MATCHING = {
            "我们是", "我们公司", "我们想", "我们最近", "客户想", "客户是", "客户需要",
            "想做", "想建", "想建设", "想上", "想要", "需要一个", "需要一套",
            "适合", "推荐方案", "能做什么", "能不能做", "可以做什么", "需求匹配", "匹配方案"
    };

    /** 案例检索信号：找做过的类似项目。 */
    private static final String[] CASE_RETRIEVAL = {
            "案例", "做过", "类似项目", "类似的项目", "成功案例", "落地案例", "客户案例",
            "参考案例", "有没有类似", "有没有做过", "同行案例", "标杆"
    };

    /** 资料查询信号：查确定性信息。 */
    private static final String[] MATERIAL_QUERY = {
            "资质", "简介", "介绍一下", "介绍下", "公司介绍", "参数", "规格", "报价", "价格",
            "证书", "证照", "什么是", "是什么", "查一下", "查下", "文档", "资料", "手册", "说明书"
    };

    /**
     * 按确定性规则分类售前任务域。
     *
     * @param question 用户原始提问（可为空）
     * @return 命中的售前任务域；无明显信号返回 {@link PresalesTaskDomain#GENERAL}
     */
    public static PresalesTaskDomain classify(String question) {
        if (question == null || question.isBlank()) {
            return PresalesTaskDomain.GENERAL;
        }
        String q = question.trim();
        if (containsAny(q, SOLUTION_DRAFTING)) return PresalesTaskDomain.SOLUTION_DRAFTING;
        if (containsAny(q, NEED_MATCHING)) return PresalesTaskDomain.NEED_MATCHING;
        if (containsAny(q, CASE_RETRIEVAL)) return PresalesTaskDomain.CASE_RETRIEVAL;
        if (containsAny(q, MATERIAL_QUERY)) return PresalesTaskDomain.MATERIAL_QUERY;
        return PresalesTaskDomain.GENERAL;
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
