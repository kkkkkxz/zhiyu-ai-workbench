package com.zwskchat.zwskchat.ai;

/**
 * 模型分层路由的线程上下文。
 *
 * <p>调用方在发起一次 LLM 调用前用 {@link #with(ModelTier)} 声明本次任务的复杂度档位，
 * Provider 在解析 model 时读取该上下文。采用 try-with-resources 自动还原，避免线程池复用时档位泄漏。</p>
 *
 * <pre>{@code
 * try (var ignored = ModelTierContext.with(ModelTier.LIGHT)) {
 *     aiService.processTextWithAi(prompt, null);
 * }
 * }</pre>
 *
 * <p>未设置时 {@link #current()} 返回 {@link ModelTier#STANDARD}，与“大多数正式问答用中强模型”一致。</p>
 */
public final class ModelTierContext {

    private static final ThreadLocal<ModelTier> HOLDER = new ThreadLocal<>();

    private ModelTierContext() {
    }

    /** 当前线程档位；未设置返回 STANDARD。 */
    public static ModelTier current() {
        ModelTier tier = HOLDER.get();
        return tier == null ? ModelTier.STANDARD : tier;
    }

    /** 声明本次任务档位，返回可关闭句柄，关闭时还原为先前值。 */
    public static Scope with(ModelTier tier) {
        ModelTier previous = HOLDER.get();
        HOLDER.set(tier == null ? ModelTier.STANDARD : tier);
        return () -> {
            if (previous == null) {
                HOLDER.remove();
            } else {
                HOLDER.set(previous);
            }
        };
    }

    /** 强制清理（线程池归还前的兜底）。 */
    public static void clear() {
        HOLDER.remove();
    }

    /** try-with-resources 句柄。 */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
