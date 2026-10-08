package com.zwskchat.zwskchat.rag;

import com.zwskchat.zwskchat.ai.hub.PresalesTaskDomain;

/**
 * 售前结构化输出契约组装器（对应工程计划书“示例提示词”章节给出的
 * &lt;output_contract&gt; / &lt;knowledge_priority&gt; / &lt;refusal_policy&gt; 结构，
 * 以及“对外输出五项原则：证据优先、引用可回溯、权限先行、低置信拒答、输出带引导动作”）。
 *
 * <p>当售前任务域为“客户需求匹配 / 简单方案草拟 / 案例检索”时，把固定的输出契约块
 * 追加到 RAG system prompt 末尾，使回答稳定地按
 * “需求判断 → 匹配方案 → 可复用案例 → 风险与边界 → 建议下一步动作 → 依据来源”组织，
 * 把每次对话转化为可追踪的留资 / 邀约 / 方案推送机会。</p>
 *
 * <p>注意：契约中的“依据来源”要求按资料标题/类型说明，而不是编造方案库/案例库 ID，
 * 以与现有提示词“界面单独展示引用、正文不加 [#1] 编号”的约定保持一致。</p>
 */
public final class PresalesPromptComposer {

    private PresalesPromptComposer() {
    }

    private static final String KNOWLEDGE_PRIORITY = """
            【知识优先级】回答取证时严格遵循：企业方案库 > 企业案例库 > 公司简介/资质 > 外部权威知识。
            """;

    private static final String OUTPUT_CONTRACT = """
            【输出契约】当用户是在做客户需求匹配、案例检索或方案草拟时，回答必须按以下结构组织，缺项也要保留小标题并说明“暂无”：
            - 需求判断：用一句话归纳客户的真实诉求与所属场景类型。
            - 匹配方案：给出最契合的 1~3 个方案方向，并说明各自适用前提。
            - 可复用案例：列出可背书的相近案例；若仅为相邻行业，必须标注“相似而非同类”。
            - 风险与边界：说明哪些属于报价/工期/合规/实验参数等需人工确认的高风险事项，不得擅自承诺。
            - 建议下一步动作：给出推动客户继续沟通的具体动作（如收集系统清单/数据量级/权限模型，或预约演示）。
            - 依据来源：按资料标题或类型说明本次回答取证自哪些资料（不编造方案库/案例库编号；引用条目在界面单独展示）。
            """;

    private static final String REFUSAL_POLICY = """
            【拒答与谨慎】当参考资料不足以支撑结论，或问题涉及报价承诺、法律结论、实验参数、未公开客户信息时：
            - 不直接下结论，明确说明“未检索到足够依据 / 需人工或专家确认”；
            - 在“建议下一步动作”中列出需要客户补充的关键信息，并建议转人工或预约演示。
            """;

    /**
     * 返回指定售前任务域对应的输出契约追加块；不需要契约的域返回空字符串。
     *
     * @param domain 售前任务域
     * @return 追加到 system prompt 末尾的契约文本，或空字符串
     */
    public static String contractBlock(PresalesTaskDomain domain) {
        if (domain == null || !domain.requiresOutputContract()) {
            return "";
        }
        return "\n" + KNOWLEDGE_PRIORITY + "\n" + OUTPUT_CONTRACT + "\n" + REFUSAL_POLICY;
    }
}
