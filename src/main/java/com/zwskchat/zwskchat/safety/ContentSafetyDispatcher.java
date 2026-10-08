package com.zwskchat.zwskchat.safety;

import com.zwskchat.zwskchat.config.properties.ContentSafetyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 内容安全审核编排器（对应工程计划书“双层防线：轻量分类器/Moderation + 主模型”）。
 *
 * <p>按 {@code zwsk.content-safety.mode} 组合关键词层与模型层：
 * <ul>
 *   <li>{@code keyword}（默认）：只走 {@link KeywordContentSafetyService}；</li>
 *   <li>{@code model}：只走 {@link ModelContentSafetyService}；</li>
 *   <li>{@code both}：先关键词快速拦截，未命中再用模型做语义兜底——命中任一层即拦截。</li>
 * </ul>
 *
 * <p>本编排器为 {@link ContentSafetyService} 的 {@link Primary} 实现，所有调用方
 * （ChatServiceImpl / VoiceOrchestratorV3 等）注入到的即是它，无需改动调用点。
 * {@link KeywordContentSafetyService} 仍作为普通 Bean 存在并被本类委托调用。</p>
 */
@Slf4j
@Primary
@Component
public class ContentSafetyDispatcher implements ContentSafetyService {

    private final ContentSafetyProperties props;
    private final KeywordContentSafetyService keyword;
    private final ModelContentSafetyService model;

    public ContentSafetyDispatcher(ContentSafetyProperties props,
                                   KeywordContentSafetyService keyword,
                                   ModelContentSafetyService model) {
        this.props = props;
        this.keyword = keyword;
        this.model = model;
    }

    @Override
    public SafetyResult check(String text) {
        if (!props.isEnabled() || text == null || text.isBlank()) {
            return SafetyResult.pass(text);
        }
        String mode = props.getMode() == null ? "keyword" : props.getMode().trim().toLowerCase();
        switch (mode) {
            case "model":
                return model.check(text);
            case "both": {
                SafetyResult kw = keyword.check(text);
                if (!kw.safe()) return kw;          // 关键词命中即拦截，省一次模型调用
                return model.check(text);           // 关键词放行后再过模型语义审核
            }
            case "keyword":
            default:
                return keyword.check(text);
        }
    }
}
