package com.zwskchat.zwskchat.ai;

/**
 * 模型能力分层（对应工程计划书“三层路由：轻 / 中强 / 强模型”）。
 *
 * <ul>
 *   <li>{@link #LIGHT}：意图识别、查询改写、关键词抽取、结果摘要等高并发低成本任务。</li>
 *   <li>{@link #STANDARD}：承载大多数正式问答。</li>
 *   <li>{@link #STRONG}：复杂方案草拟、多文档综合、高价值客户场景。</li>
 * </ul>
 *
 * <p>通过 {@link ModelTierContext} 以 ThreadLocal 形式在调用链上传递，
 * Provider 在解析 model 时据此选择 {@code zwsk.ai.chat.tier.*}；未设置时按 STANDARD 处理。</p>
 */
public enum ModelTier {
    LIGHT,
    STANDARD,
    STRONG
}
