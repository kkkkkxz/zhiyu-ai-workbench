package com.zwskchat.zwskchat.safety;

import com.zwskchat.zwskchat.config.properties.ContentSafetyProperties;
import com.zwskchat.zwskchat.safety.ContentSafetyService.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容安全编排器路由单元测试（纯 mock，不加载 Spring、不触达 LLM）。
 *
 * <p>验证不变量：
 * <ol>
 *   <li>enabled=false / 空文本 → 直接放行，两层都不调用。</li>
 *   <li>mode=keyword → 只走关键词层。</li>
 *   <li>mode=model → 只走模型层。</li>
 *   <li>mode=both 且关键词命中 → 拦截并短路，不调用模型层（省成本）。</li>
 *   <li>mode=both 且关键词放行 → 再过模型层兜底。</li>
 * </ol>
 */
class ContentSafetyDispatcherTest {

    private ContentSafetyProperties props;
    private KeywordContentSafetyService keyword;
    private ModelContentSafetyService model;
    private ContentSafetyDispatcher dispatcher;

    @BeforeEach
    void setup() {
        props = new ContentSafetyProperties();
        keyword = mock(KeywordContentSafetyService.class);
        model = mock(ModelContentSafetyService.class);
        dispatcher = new ContentSafetyDispatcher(props, keyword, model);
    }

    @Test
    void disabledPassesThroughWithoutCallingEitherLayer() {
        props.setEnabled(false);
        SafetyResult r = dispatcher.check("任何文本");
        assertTrue(r.safe());
        verify(keyword, never()).check(anyString());
        verify(model, never()).check(anyString());
    }

    @Test
    void blankTextPassesThrough() {
        SafetyResult r = dispatcher.check("   ");
        assertTrue(r.safe());
        verify(keyword, never()).check(anyString());
        verify(model, never()).check(anyString());
    }

    @Test
    void keywordModeOnlyUsesKeywordLayer() {
        props.setMode("keyword");
        when(keyword.check("hi")).thenReturn(SafetyResult.pass("hi"));
        SafetyResult r = dispatcher.check("hi");
        assertTrue(r.safe());
        verify(keyword).check("hi");
        verify(model, never()).check(anyString());
    }

    @Test
    void modelModeOnlyUsesModelLayer() {
        props.setMode("model");
        when(model.check("hi")).thenReturn(SafetyResult.pass("hi"));
        SafetyResult r = dispatcher.check("hi");
        assertTrue(r.safe());
        verify(model).check("hi");
        verify(keyword, never()).check(anyString());
    }

    @Test
    void bothModeShortCircuitsWhenKeywordBlocks() {
        props.setMode("both");
        when(keyword.check("脏话")).thenReturn(SafetyResult.block("keyword:x", "已拦截"));
        SafetyResult r = dispatcher.check("脏话");
        assertFalse(r.safe());
        assertEquals("已拦截", r.sanitized());
        verify(keyword).check("脏话");
        // 关键词已拦截 → 不应再调用模型层
        verify(model, never()).check(anyString());
    }

    @Test
    void bothModeFallsThroughToModelWhenKeywordPasses() {
        props.setMode("both");
        when(keyword.check("绕过的攻击")).thenReturn(SafetyResult.pass("绕过的攻击"));
        when(model.check("绕过的攻击")).thenReturn(SafetyResult.block("model:UNSAFE", "已拦截"));
        SafetyResult r = dispatcher.check("绕过的攻击");
        assertFalse(r.safe());
        verify(keyword).check("绕过的攻击");
        verify(model).check("绕过的攻击");
    }

    @Test
    void unknownModeDefaultsToKeyword() {
        props.setMode("typo-mode");
        when(keyword.check("hi")).thenReturn(SafetyResult.pass("hi"));
        dispatcher.check("hi");
        verify(keyword).check("hi");
        verify(model, never()).check(anyString());
    }
}
