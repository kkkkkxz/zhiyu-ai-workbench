package com.zwskchat.zwskchat.voice.tts.impl;

import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.voice.tts.TtsCallback;
import com.zwskchat.zwskchat.voice.tts.TtsProvider;
import com.zwskchat.zwskchat.voice.tts.TtsTask;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云百炼 CosyVoice 流式 TTS Provider。
 * 默认输出 mp3，前端 voice-demo.html 直接 Audio 标签播放。
 * 推荐模型：cosyvoice-v2（最稳定）；voice：longxiaochun（女）/ longwan / loongstella 等。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeCosyVoiceTtsProvider implements TtsProvider {

    private final AiProperties aiProps;
    private final VoiceProperties voiceProps;
    private final TenantAiConfigService tenantAiConfigService;

    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;

    @Override public String name() { return "dashscope-cosyvoice"; }

    @PostConstruct
    void init() {
        VoiceProperties.DashScope ds = voiceProps.getDashscope();
        log.info("[DashScope-TTS] config model={}, voice={}, format={}, speechRate={}",
                ds.getTtsModel(), ds.getTtsVoice(), ds.getTtsFormat(), ds.getTtsSpeechRate());
    }

    @Override public String audioFormat() {
        String fmt = voiceProps.getDashscope().getTtsFormat();
        return (fmt == null || fmt.isBlank()) ? "mp3" : fmt.toLowerCase();
    }

    @Override
    public TtsTask synthesize(String text, TtsCallback cb) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean done = new AtomicBoolean();
        // 把 synthesizer 引用暴露到外部，cancel() 时主动断开 SDK 的 WebSocket，
        // 否则用户 INTERRUPT 后 SDK 仍在拉音频帧、烧 token、占带宽，直到它自己自然超时。
        AtomicReference<SpeechSynthesizer> synthRef = new AtomicReference<>();

        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                ? tenantConfig.getApiKey()
                : aiProps.getDashscope().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            cb.onError(new IllegalStateException("DashScope API Key 未配置"));
            done.set(true);
            return inertTask(cancelled, done, synthRef);
        }

        VoiceProperties.DashScope ds = voiceProps.getDashscope();
        String model = (ds.getTtsModel() == null || ds.getTtsModel().isBlank()) ? "cosyvoice-v2" : ds.getTtsModel();
        String voice = (ds.getTtsVoice() == null || ds.getTtsVoice().isBlank()) ? "longxiaochun_v2" : ds.getTtsVoice();
        SpeechSynthesisAudioFormat audioFormat = resolveFormat(ds.getTtsFormat());

        // 给本次合成生成短 id，方便在密集并发时把 SDK 客户端日志和我们这一侧 log 关联起来。
        String synthId = Long.toHexString(System.nanoTime() & 0xFFFFFFFFL);
        // 用于在 onComplete 时统计本次合成累计推了多少音频字节、多少帧——
        // "onComplete 但 audio=0 bytes" 是排查 server 静默无回包问题的关键证据。
        java.util.concurrent.atomic.AtomicLong totalBytes = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicInteger totalFrames = new java.util.concurrent.atomic.AtomicInteger();
        long startMs = System.currentTimeMillis();

        // 关键：ttsv2 的 SpeechSynthesizer.call(text) 实测是"异步 submit text"——
        // 立刻返回（约 1ms），真正的音频帧是 SDK 在内部 OkHttp WebSocket 线程上
        // 通过 ResultCallback.onEvent 异步推回；最终 onComplete / onError 才表示结束。
        // 我们必须用 latch 等真正的完成信号，绝不能在 call() 返回后立刻关闭 duplex —— 否则 SDK
        // 还没握手完就被强制断链（日志会出现 "call closed before" + "Sending streaming data exception"），
        // 永远收不到任何音频帧。
        CountDownLatch completionLatch = new CountDownLatch(1);

        executor.execute(() -> {
            SpeechSynthesizer synthesizer = null;
            try {
                ResultCallback<SpeechSynthesisResult> rc = new ResultCallback<>() {
                    @Override
                    public void onEvent(SpeechSynthesisResult r) {
                        if (cancelled.get()) return;
                        ByteBuffer buf = r == null ? null : r.getAudioFrame();
                        if (buf != null && buf.remaining() > 0) {
                            int n = buf.remaining();
                            byte[] data = new byte[n];
                            buf.duplicate().get(data);
                            int frameNo = totalFrames.incrementAndGet();
                            totalBytes.addAndGet(n);
                            // 第 1 / 5 / 20 / 50 帧打 log（节流避免刷屏），
                            // 第 1 帧最关键——证明 SDK 真的收到 server 推流了
                            if (frameNo == 1 || frameNo == 5 || frameNo == 20 || frameNo == 50 || frameNo % 100 == 0) {
                                log.info("[DashScope-TTS] id={} frame#{} {} bytes (total {} bytes)",
                                        synthId, frameNo, n, totalBytes.get());
                            }
                            try { cb.onAudio(data); } catch (Exception ignore) {}
                        } else {
                            // 空 / null 帧通常意味着 server 回了非 audio 事件（如 task-started）
                            // 但 SDK 仍把它转发到 onEvent。打 trace 级别便于深挖。
                            log.trace("[DashScope-TTS] id={} onEvent with no audio (likely task-started/heartbeat)", synthId);
                        }
                    }
                    @Override
                    public void onComplete() {
                        long elapsed = System.currentTimeMillis() - startMs;
                        if (totalFrames.get() == 0) {
                            log.warn("[DashScope-TTS] id={} onComplete with NO audio frames in {}ms — likely server rejected task or no audio returned",
                                    synthId, elapsed);
                        } else {
                            log.info("[DashScope-TTS] id={} onComplete frames={} bytes={} elapsed={}ms",
                                    synthId, totalFrames.get(), totalBytes.get(), elapsed);
                        }
                        try {
                            if (!cancelled.get() && done.compareAndSet(false, true)) {
                                cb.onComplete();
                            }
                        } catch (Exception ignore) {
                        } finally {
                            // 必须在 cb.onComplete 之后再 countDown，确保 worker 线程在
                            // "看到 latch 醒"时所有上游回调都已经执行完毕。
                            completionLatch.countDown();
                        }
                    }
                    @Override
                    public void onError(Exception e) {
                        try {
                            if (!cancelled.get() && done.compareAndSet(false, true)) {
                                // 改 INFO/WARN 级别同时带 stacktrace，便于排查
                                // SDK 内部静默吞错或连接异常的具体根因。
                                log.warn("[DashScope-TTS] id={} error after {} frames / {} bytes: {}",
                                        synthId, totalFrames.get(), totalBytes.get(), e.getMessage(), e);
                                cb.onError(e);
                            }
                        } catch (Exception ignore) {
                        } finally {
                            completionLatch.countDown();
                        }
                    }
                };

                SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .voice(voice)
                        .format(audioFormat)
                        .speechRate(ds.getTtsSpeechRate())
                        .build();

                log.info("[DashScope-TTS] id={} call begin model={} voice={} format={} chars={}",
                        synthId, model, voice, audioFormat, text == null ? 0 : text.length());
                synthesizer = new SpeechSynthesizer(param, rc);
                synthRef.set(synthesizer);
                // 如果在 new 之后、call 之前用户已经按 cancel，立刻收尾不发起调用
                if (cancelled.get()) {
                    safeCloseDuplex(synthesizer);
                    completionLatch.countDown();
                    return;
                }
                // ttsv2 SpeechSynthesizer.call(text) 是异步 submitText：把文本提交给
                // SDK 内部 emitter，立刻返回（~1ms）。真正的音频帧由 SDK 通过 ResultCallback.onEvent
                // 在 OkHttp WebSocket 线程上推回，最终触发 onComplete / onError。
                // call() 的返回值在大多数情况下是 null 或空 buffer——不能依赖。
                synthesizer.call(text);

                // 等 SDK 真正完成（onComplete / onError 都会 countDown）。
                // 60 秒上限：cosyvoice-v2 实际推流首包通常 < 1 秒，整段 < 30 秒。
                boolean completed = completionLatch.await(60, TimeUnit.SECONDS);

                if (!completed) {
                    long elapsed = System.currentTimeMillis() - startMs;
                    log.warn("[DashScope-TTS] id={} timed out after {}ms (frames={}, bytes={}); forcing fallback",
                            synthId, elapsed, totalFrames.get(), totalBytes.get());
                    // 超时兜底：试着从 SDK 内部累积 buffer 拿一次
                    if (!cancelled.get() && totalFrames.get() == 0) {
                        ByteBuffer fallback = safeGetAudioData(synthesizer);
                        if (fallback != null && fallback.remaining() > 0) {
                            int n = fallback.remaining();
                            byte[] all = new byte[n];
                            fallback.duplicate().get(all);
                            log.warn("[DashScope-TTS] id={} timeout fallback getAudioData(): {} bytes",
                                    synthId, n);
                            try { cb.onAudio(all); totalFrames.incrementAndGet(); totalBytes.addAndGet(n); } catch (Exception ignore) {}
                        }
                    }
                    // 超时强制结束：通知上游 onComplete，让 orchestrator latch 解除
                    if (!cancelled.get() && done.compareAndSet(false, true)) {
                        try { cb.onComplete(); } catch (Exception ignore) {}
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (!cancelled.get() && done.compareAndSet(false, true)) {
                    log.warn("[DashScope-TTS] id={} interrupted while waiting for completion", synthId);
                    try { cb.onError(ie); } catch (Exception ignore) {}
                }
            } catch (Throwable t) {
                if (!cancelled.get() && done.compareAndSet(false, true)) {
                    log.warn("[DashScope-TTS] id={} call threw: {}", synthId, t.getMessage(), t);
                    try { cb.onError(t instanceof Exception e ? e : new RuntimeException(t)); } catch (Exception ignore) {}
                }
            } finally {
                // 关闭 SDK 内部连接：此处一定是在 callback 已经 countDown 之后，所以
                // 不会再像之前那样"过早断链"导致 SDK 收不到音频。幂等。
                safeCloseDuplex(synthesizer);
                synthRef.set(null);
            }
        });

        return inertTask(cancelled, done, synthRef);
    }

    /**
     * 安全关闭 SDK 内部 WebSocket 双工连接。
     * <p>SpeechSynthesizer 与 ASR 的 Recognition 共用同一类双工 API，调用 close 后
     * SDK 会主动结束远端会话；如果 SDK 没暴露 getDuplexApi（不同 SDK 版本差异），
     * 走 NoSuchMethodError 兜底，不影响主流程。</p>
     */
    private static void safeCloseDuplex(SpeechSynthesizer synthesizer) {
        if (synthesizer == null) return;
        try {
            if (synthesizer.getDuplexApi() != null) {
                synthesizer.getDuplexApi().close(1000, "client cancel");
            }
        } catch (Throwable ignore) {
            // 包括 NoSuchMethodError / NullPointerException / 已关闭等情况
        }
    }

    /**
     * 反射调 SDK 的 {@code getAudioData()}：不同 SDK 小版本可能签名/可见性微调，
     * 用反射 + 静默 catch 避免编译期硬绑定。返回 null 时调用方需有兜底分支。
     */
    private static ByteBuffer safeGetAudioData(SpeechSynthesizer synthesizer) {
        if (synthesizer == null) return null;
        try {
            return synthesizer.getAudioData();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static SpeechSynthesisAudioFormat resolveFormat(String raw) {
        if (raw == null || raw.isBlank()) return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
        String key = raw.trim().toUpperCase().replace('-', '_');
        try {
            return SpeechSynthesisAudioFormat.valueOf(key);
        } catch (IllegalArgumentException ex) {
            log.warn("[DashScope-TTS] unknown format '{}', fallback to MP3_22050HZ_MONO_256KBPS", raw);
            return SpeechSynthesisAudioFormat.MP3_22050HZ_MONO_256KBPS;
        }
    }

    private TtsTask inertTask(AtomicBoolean cancelled, AtomicBoolean done,
                              AtomicReference<SpeechSynthesizer> synthRef) {
        return new TtsTask() {
            @Override public void cancel() {
                if (!cancelled.compareAndSet(false, true)) return;
                done.set(true);
                // 主动断 WebSocket，让 SDK 立即停止接收音频帧；如果 worker 线程还在 new
                // 阶段，synthRef 暂时为 null，由 worker 自己在 cancelled 检查处兜底。
                safeCloseDuplex(synthRef.get());
            }
            @Override public boolean isDone() { return done.get(); }
        };
    }
}
