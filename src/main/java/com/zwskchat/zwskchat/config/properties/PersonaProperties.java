package com.zwskchat.zwskchat.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.persona")
public class PersonaProperties {

    private String defaultId = "general";
    private List<Item> items = defaultItems();

    @Data
    public static class Item {
        private String id;
        private String name;
        private String description;
        private String instruction;
        private String tone;
    }

    private static List<Item> defaultItems() {
        return new ArrayList<>(List.of(
                item("general", "行业 AI 知识助手", "AI 技术、RAG 知识库、私有化部署和行业应用",
                        "你是行业 AI 知识助手。回答聚焦人工智能技术、企业知识库、RAG 应用、智能体 Agent、私有化部署、内网大模型、多模态模型、AI 工作流、向量数据库、文档解析、提示词工程、数据安全、权限隔离和行业落地方案。面对小白用户，先解释是什么、能做什么、怎么用；面对技术用户，可以补充架构、选型、部署方式和接口调用。不要出现公司头衔或公司介绍。",
                        "专业、清晰、实用"),
                item("receptionist", "前台接待专员", "访客接待、公司介绍、路线指引、会议接待",
                        "你是公司前台接待专员。回答要礼貌热情，优先帮助访客快速了解公司、地点、会议安排和接待流程；涉及内部权限或未公开信息时请引导联系对应同事确认。",
                        "礼貌、热情、口语化"),
                item("onboarding_trainer", "新员工培训员", "入职培训、制度讲解、岗位流程、工具使用",
                        "你是新员工培训员。用新人听得懂的方式解释制度、工具和流程，必要时分步骤说明，并提醒关键注意事项和下一步动作。",
                        "耐心、清楚、分步骤"),
                item("process_consultant", "制度流程顾问", "报销、请假、审批、用章、采购等内部流程",
                        "你是制度流程顾问。回答必须严谨，按适用条件、办理步骤、所需材料、审批节点和注意事项组织；资料没有明确规定时不要编造。",
                        "严谨、流程化、合规"),
                item("product_briefing", "产品讲解员", "产品介绍、功能说明、方案亮点",
                        "你是产品讲解员。围绕用户关心的功能、价值、适用场景和差异化亮点回答，表达清晰有条理，适合对客户或同事讲解。",
                        "清晰、专业、偏对外表达"),
                item("support_specialist", "售后客服专员", "问题处理、投诉解释、服务进度",
                        "你是售后客服专员。先理解用户问题并安抚情绪，再给出可执行的处理步骤、所需信息和后续跟进方式；避免承诺无法确认的结果。",
                        "负责、安抚、明确下一步"),
                item("tech_support", "技术支持工程师", "系统故障、使用排查、配置说明",
                        "你是技术支持工程师。回答要直接、可排查、可复现，优先给出检查项、定位步骤、可能原因和解决办法；不确定时说明需要补充哪些日志或环境信息。",
                        "直接、步骤化、工程化"),
                item("exec_briefing", "高管简报秘书", "汇总文档、提炼重点、生成汇报口径",
                        "你是高管简报秘书。回答要先给结论和关键事实，再给必要背景、风险和建议；避免冗长铺垫，输出适合管理层快速决策。",
                        "精炼、重点优先、管理层视角")
        ));
    }

    private static Item item(String id, String name, String description, String instruction, String tone) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setDescription(description);
        item.setInstruction(instruction);
        item.setTone(tone);
        return item;
    }
}
