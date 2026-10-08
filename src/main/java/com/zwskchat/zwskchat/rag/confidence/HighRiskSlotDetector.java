package com.zwskchat.zwskchat.rag.confidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 高风险槽位检测器（对应工程计划书“规则引擎·风险拦截器”与可信度章节：
 * “对价格、政策解读、实验参数、资质承诺、交付边界等高风险槽位，可再叠加规则阈值，
 * 哪怕整体置信度足够，也必须要求人工复核”）。
 *
 * <p>采用确定性关键词命中（不依赖 LLM、可单测、零额外延迟），按槽位类别归类命中结果。
 * 命中后由 {@link ResponseGovernanceService} 强制升级为“谨慎 + 人工复核”，
 * 直接防住“擅自承诺价格/工期、给出法律/合规结论、编造实验参数/资质”等越界风险。</p>
 */
public final class HighRiskSlotDetector {

    private HighRiskSlotDetector() {
    }

    /** 槽位类别 → 触发关键词。使用有序 Map 保证检测结果稳定可测。 */
    private static final Map<String, String[]> SLOT_KEYWORDS = new LinkedHashMap<>();

    static {
        SLOT_KEYWORDS.put("报价/价格", new String[]{
                "报价", "价格", "多少钱", "费用", "预算是多少", "几折", "折扣", "成交价", "单价", "总价"});
        SLOT_KEYWORDS.put("政策/法律/合规", new String[]{
                "政策", "法律", "法规", "合规结论", "是否合法", "是否违法", "合不合规", "条文", "处罚"});
        SLOT_KEYWORDS.put("实验参数", new String[]{
                "实验参数", "实验数据", "配方", "工艺参数", "化验", "检测值", "试验条件", "剂量"});
        SLOT_KEYWORDS.put("资质承诺", new String[]{
                "保证", "承诺", "一定能", "百分百", "包过", "资质背书", "兜底", "绝对"});
        SLOT_KEYWORDS.put("交付边界/工期", new String[]{
                "交付时间", "工期", "多久能交付", "上线时间", "几天完成", "几周完成", "几个月完成", "交付周期", "deadline"});
    }

    /**
     * 检测文本命中的高风险槽位类别。
     *
     * @param text 待检测文本（用户问题或回答；可为空）
     * @return 命中的槽位类别列表（去重、保持声明顺序）；无命中返回空列表
     */
    public static List<String> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> hit = new ArrayList<>();
        for (Map.Entry<String, String[]> e : SLOT_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (text.contains(kw)) {
                    hit.add(e.getKey());
                    break;
                }
            }
        }
        return hit;
    }

    /** 是否命中任意高风险槽位。 */
    public static boolean hasHighRisk(String text) {
        return !detect(text).isEmpty();
    }
}
