package com.zwskchat.zwskchat.config.properties;

import com.zwskchat.zwskchat.ai.ModelTier;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "zwsk.ai")
public class AiProperties {

    private Chat chat = new Chat();
    private Embedding embedding = new Embedding();
    /** 检索召回 topK（首轮）。 */
    private int retrieveTopK = 12;
    /** 最终送入 LLM 的最大上下文片段数。 */
    private int contextTopK = 6;
    /** 是否启用本地轻量重排。 */
    private boolean localRerankEnabled = true;
    /** 本地重排候选放大倍数：最终 contextTopK 前先召回更多候选再重排。 */
    private int rerankCandidateMultiplier = 3;
    /** 本地重排关键词覆盖权重，剩余权重使用向量分。 */
    private double rerankLexicalWeight = 0.25;
    /** 单切片最大 token（粗略以 3.5 字符约等于 1 token 估算）。 */
    private int chunkMaxTokens = 400;
    /** 切片重叠 token。 */
    private int chunkOverlapTokens = 60;
    /** 单次问答最大输出 token。 */
    private int maxOutputTokens = 1536;
    /** 单次回答超时（秒）。 */
    private int chatTimeoutSeconds = 120;
    /** 检索相似度阈值，低于该值的片段不参与上下文。 */
    private double scoreThreshold = 0.30;
    /**
     * 证据打包是否按资产类型优先序重排（企业资料 &gt; 案例 &gt; 方案 &gt; 外部）。
     * 默认开启；各档位内部保持重排相关度顺序（稳定排序）。
     */
    private boolean evidencePriorityEnabled = true;
    /** 是否启用混合检索（dense + 关键词召回 + RRF 融合）。默认关闭，开启需 DB 支持关键词召回。 */
    private boolean hybridRecallEnabled = false;
    /** 关键词召回每个知识库的最大候选数。 */
    private int hybridKeywordRecallLimit = 20;
    /** RRF 融合平滑常数。 */
    private int hybridRrfK = 60;
    /** 语音链路判定 RAG“明确命中”的最低分。 */
    private double voiceRagExplicitScoreThreshold = 0.45;
    /** PDF 处理模式：true=文本模式（低成本），false=图片模式（高成本）。 */
    private boolean pdfPrefillMode = true;

    /** 阿里云百炼 DashScope 共享配置：API Key、region、各类模型名。 */
    private DashScope dashscope = new DashScope();

    /** 可信度组合评分与三档拒答策略（对应工程计划书“可信度评分”章节）。 */
    private Confidence confidence = new Confidence();

    /** 回答治理：低置信拒答引导 + CTA 引导动作（对应工程计划书“证据优先、输出带引导动作”原则）。 */
    private Governance governance = new Governance();

    /** 售前四大任务域 + 结构化输出契约（对应工程计划书“四个主任务域 / 输出契约”）。 */
    private Presales presales = new Presales();

    /**
     * 按任务档位解析实际使用的 chat 模型名。
     *
     * <p>路由规则（对应工程计划书三层路由）：
     * <ul>
     *   <li>分层未开启（tier.enabled=false）→ 始终返回 baseModel；</li>
     *   <li>LIGHT → tier.lightModel，STANDARD → tier.standardModel，STRONG → tier.strongModel；</li>
     *   <li>对应档位为空 → 回退到 baseModel，保证永不返回空。</li>
     * </ul>
     *
     * @param tier      任务档位（null 视为 STANDARD）
     * @param baseModel 该 provider 已解析出的基础模型（含租户运行时配置 / 全局默认）
     */
    public String resolveTierModel(ModelTier tier, String baseModel) {
        if (chat == null || chat.getTier() == null || !chat.getTier().isEnabled()) {
            return baseModel;
        }
        ModelTier t = tier == null ? ModelTier.STANDARD : tier;
        Tier cfg = chat.getTier();
        String picked = switch (t) {
            case LIGHT -> cfg.getLightModel();
            case STRONG -> cfg.getStrongModel();
            case STANDARD -> cfg.getStandardModel();
        };
        return picked == null || picked.isBlank() ? baseModel : picked.trim();
    }

    @Data
    public static class Chat {
        /** openai-compat / dashscope-sdk */
        private String provider = "openai-compat";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;
        private String model = "gpt-4o-mini";
        private double temperature = 0.1;

        /**
         * 多模型分层路由（对应工程计划书“三层路由：轻模型 / 中强模型 / 强模型”）。
         * 默认 enabled=false：所有任务都用 {@link #model}，保持既有行为。
         * 开启后按任务复杂度路由：意图识别/查询改写/摘要 → light；常规问答 → standard；
         * 复杂方案草拟/多文档综合/高价值场景 → strong。任一档为空则回退到 {@link #model}。
         */
        private Tier tier = new Tier();
    }

    /**
     * 三层模型路由配置。各档留空时回退到 {@link Chat#getModel()}。
     */
    @Data
    public static class Tier {
        /** 是否启用分层路由。关闭时所有任务统一用 chat.model。 */
        private boolean enabled = false;
        /** 轻模型：意图识别、查询改写、关键词抽取、结果摘要等高并发低成本任务。 */
        private String lightModel;
        /** 中强模型：承载大多数正式问答。 */
        private String standardModel;
        /** 强模型：复杂方案草拟、多文档综合、高价值客户场景。 */
        private String strongModel;
    }

    @Data
    public static class Embedding {
        /** openai-compat / dashscope-sdk */
        private String provider = "openai-compat";
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;
        private String model = "text-embedding-3-small";
        /** 向量维度，必须与模型一致：text-embedding-3-small=1536，DashScope text-embedding-v3=1024。 */
        private int dimension = 1536;
        /** 单批 embed 数量。DashScope text-embedding-v3 建议不超过 25。 */
        private int batchSize = 32;
    }

    @Data
    public static class DashScope {
        /** 单一 API Key，对话、向量、ASR、TTS 共用。 */
        private String apiKey;
        /** china（默认）/ international */
        private String region = "china";
        private String chatModel = "qwen-plus";
        private String embeddingModel = "text-embedding-v3";
        /** OpenAI 兼容多模态接口的基础地址。 */
        private String compatibleBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        /** 图片/扫描件文字提取模型。 */
        private String ocrModel = "qwen-vl-ocr";
        /** 图片理解兜底模型。 */
        private String visionModel = "qwen-vl-plus";
        /** 附件视觉解析单次请求超时时间（秒）。 */
        private int analysisTimeoutSeconds = 180;
        /** 扫描 PDF / 图片最多提交多少页或多少张图片。 */
        private int analysisMaxImages = 6;
    }

    /**
     * 可信度组合评分配置。默认权重严格对应工程计划书给出的公式：
     * <pre>
     * confidence = 0.30*retrieval_coverage
     *            + 0.20*rerank_margin
     *            + 0.20*evidence_consistency
     *            + 0.15*source_authority
     *            + 0.10*freshness_score
     *            + 0.05*policy_pass_score
     * </pre>
     * 阈值：≥ answerThreshold 直接回答；[cautiousThreshold, answerThreshold) 谨慎回答；
     * &lt; cautiousThreshold 拒答 / 转人工。
     */
    @Data
    public static class Confidence {
        /** 是否启用可信度组合评分。关闭时不参与决策，保持旧行为。 */
        private boolean enabled = true;
        /** 检索覆盖度权重。 */
        private double weightRetrievalCoverage = 0.30;
        /** 重排区分度（top1 与其余的分差）权重。 */
        private double weightRerankMargin = 0.20;
        /** 证据一致性权重。 */
        private double weightEvidenceConsistency = 0.20;
        /** 来源权威度权重。 */
        private double weightSourceAuthority = 0.15;
        /** 时效性权重。 */
        private double weightFreshness = 0.10;
        /** 安全/策略通过度权重。 */
        private double weightPolicyPass = 0.05;
        /** ≥ 该阈值直接回答。 */
        private double answerThreshold = 0.80;
        /** ≥ 该阈值且 &lt; answerThreshold 谨慎回答；&lt; 该阈值拒答。 */
        private double cautiousThreshold = 0.60;
        /**
         * 来源权威度默认值：当前知识对象模型尚未落地 authority_level 字段，
         * 企业内部知识库整体视为较权威，给一个保守默认值。
         */
        private double defaultSourceAuthority = 0.75;
        /**
         * 时效性默认值：尚未落地 freshness_date 字段前的保守默认值。
         */
        private double defaultFreshness = 0.60;
        /**
         * 时效性半衰期（天）：内容自 freshness_date 起每经过该天数，时效分约减半。
         * 用于把 freshness_date 换算为 [0,1] 的时效分。
         */
        private double freshnessHalfLifeDays = 180;
        /** 权威等级最大值（与 knowledge_document.authority_level 取值上限一致，用于归一化）。 */
        private int authorityMaxLevel = 5;
    }

    /**
     * 回答治理配置：根据可信度决策生成谨慎提示 / 拒答引导，并附带 CTA 引导动作。
     * 对应工程计划书“对外输出五项原则”中的“低置信拒答、输出带引导动作”。
     */
    @Data
    public static class Governance {
        /** 是否启用回答治理（生成 CTA / 谨慎提示 / 拒答引导）。 */
        private boolean enabled = true;
        /**
         * 治理 / 安全策略版本号，写入回答回溯字段 ai_message.policy_version，
         * 便于在策略调整后定位“某条回答是用哪一版策略生成的”。每次显著调整阈值/话术时应递增。
         */
        private String policyVersion = "2026.06";
        /**
         * 是否对 REFUSE 档执行“硬拒答”（用引导话术替换正常回答）。
         * 默认 false：仅附加引导，不替换正常回答，避免回归既有体验；
         * 置 true 后，低置信问题将直接返回补充信息 + 转人工/预约引导。
         */
        private boolean enforceRefusal = false;
        /** 谨慎档（CAUTIOUS）追加的提示语。 */
        private String cautionNote =
                "以上内容部分依据通用知识推断，企业内部的具体数据、报价、交付与合规结论请以正式资料或人工确认为准。";
        /** 拒答档（REFUSE）的引导话术开头。 */
        private String refusalMessage =
                "暂时没有检索到足够的企业资料来确定回答这个问题。为避免给出不准确的信息，建议先补充相关材料或转人工/专家确认。";
        /** 拒答档建议补充的信息项。 */
        private List<String> requiredInfo = new ArrayList<>(List.of(
                "涉及的行业 / 客户类型",
                "具体业务场景与目标",
                "现有系统、数据量级与权限要求"));
        /** CTA 引导动作（对应工程计划书“答案 + 引用 + CTA”）。 */
        private List<String> ctaSuggestions = new ArrayList<>(List.of(
                "预约 30 分钟方案演示",
                "留下联系方式获取案例包",
                "导出初版方案 / 案例摘要"));

        /**
         * 高风险槽位强制人工复核（对应工程计划书：报价/政策解读/实验参数/资质承诺/交付边界等高风险槽位，
         * 哪怕整体置信度足够，也必须要求人工复核）。默认开启。
         * 命中后即使可信度判为 ANSWER，也会强制升级为“谨慎 + 人工复核提示”。
         */
        private boolean highRiskSlotGuardEnabled = true;
        /** 命中高风险槽位时追加的提示语。 */
        private String highRiskNote =
                "该问题涉及报价 / 政策法律 / 实验参数 / 资质承诺 / 交付工期等高风险事项，以上仅供参考，"
                        + "最终结论须经业务 / 法务 / 专家人工复核确认，不构成任何承诺。";
    }

    /**
     * 售前任务域与结构化输出契约配置（对应工程计划书“四个主任务域”与“输出契约”）。
     *
     * <p>启用后，RAG 链路会用确定性关键词分类器识别用户属于
     * “客户需求匹配 / 案例检索 / 资料查询 / 简单方案草拟”中的哪一类；
     * 当属于需求匹配 / 案例检索 / 方案草拟时，向 system prompt 追加固定输出契约
     * （需求判断 / 匹配方案 / 可复用案例 / 风险与边界 / 建议下一步动作 / 依据来源），
     * 把对话转化为可追踪的留资 / 邀约 / 方案推送机会。</p>
     */
    @Data
    public static class Presales {
        /** 是否启用售前任务域识别（默认开启，落实工程计划书四大任务域定义）。 */
        private boolean enabled = true;
        /**
         * 是否在需求匹配 / 案例检索 / 方案草拟场景注入结构化输出契约。
         * 默认开启；置 false 时仅做任务域识别（用于上报/埋点）而不改变回答结构。
         */
        private boolean outputContractEnabled = true;
    }
}
