package com.zwskchat.zwskchat.ai.hub;

import com.zwskchat.zwskchat.ai.AiService;
import com.zwskchat.zwskchat.ai.ModelTier;
import com.zwskchat.zwskchat.ai.ModelTierContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentRouter {

    private final AiService aiService;

    private static final String CLASSIFY_PROMPT = """
            你是一个意图分类器。根据用户的输入，判断其意图属于以下哪一类，只返回类别名称：
            - RAG: 用户想查阅文档/知识库中的内容
            - DATA_QUERY: 用户想查询业务数据（如销售额、客户数、合同统计、日程安排等）
            - BIZ_ACTION: 用户想执行业务操作（如创建客户、派发工单、创建日程、导出文档等）
            - CHART: 用户想生成图表或可视化报表
            - GENERAL: 闲聊或与业务无关的问题
            
            用户输入: %s
            
            意图类别:""";

    public IntentType classify(String userInput) {
        // 意图识别属于轻量任务，走 LIGHT 档模型（分层未开启时等同默认模型）。
        try (ModelTierContext.Scope ignored = ModelTierContext.with(ModelTier.LIGHT)) {
            String prompt = String.format(CLASSIFY_PROMPT, userInput);
            String result = aiService.processTextWithAi(prompt, null).trim().toUpperCase();
            for (IntentType type : IntentType.values()) {
                if (result.contains(type.name())) {
                    log.info("[IntentRouter] input='{}' -> {}", userInput.substring(0, Math.min(50, userInput.length())), type);
                    return type;
                }
            }
        } catch (Exception e) {
            log.warn("[IntentRouter] classify failed, fallback to GENERAL", e);
        }
        return IntentType.GENERAL;
    }
}
