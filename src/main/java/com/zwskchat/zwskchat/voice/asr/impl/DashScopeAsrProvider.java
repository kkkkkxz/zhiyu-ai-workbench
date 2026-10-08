package com.zwskchat.zwskchat.voice.asr.impl;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.tenant.dto.TenantAiRuntimeConfig;
import com.zwskchat.zwskchat.tenant.service.TenantAiConfigService;
import com.zwskchat.zwskchat.voice.asr.AsrCallback;
import com.zwskchat.zwskchat.voice.asr.AsrProvider;
import com.zwskchat.zwskchat.voice.asr.AsrSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云百炼 Paraformer 实时语音识别 Provider（流式）。
 * 模型：paraformer-realtime-v2（中文/英文混读、标点、热词等）。
 *
 * 真正的流式 ASR：边说边出 partial 结果，用户停顿后产生 final。
 * 客户端音频按 PCM 16bit 16kHz mono 直接喂入 SDK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeAsrProvider implements AsrProvider {

    private final AiProperties aiProps;
    private final VoiceProperties voiceProps;
    private final TenantAiConfigService tenantAiConfigService;

    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;

    @Override public String name() { return "dashscope-paraformer"; }

    @Override
    public AsrSession open(int sampleRate, AsrCallback cb) {
        TenantAiRuntimeConfig tenantConfig = tenantAiConfigService.getCurrentRuntimeConfig().orElse(null);
        String apiKey = tenantConfig != null && tenantConfig.getApiKey() != null && !tenantConfig.getApiKey().isBlank()
                ? tenantConfig.getApiKey()
                : aiProps.getDashscope().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            cb.onError(new IllegalStateException("DashScope API Key 未配置"));
            return noopSession();
        }

        Recognition recognizer = new Recognition();
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean stopped = new AtomicBoolean();
        AtomicBoolean started = new AtomicBoolean();
        CountDownLatch startedLatch = new CountDownLatch(1);
        ConcurrentLinkedQueue<byte[]> pending = new ConcurrentLinkedQueue<>();
        StringBuilder finalBuf = new StringBuilder();
        StringBuilder latestPartial = new StringBuilder();

        ResultCallback<RecognitionResult> resultCb = new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult result) {
                if (closed.get()) return;
                if (started.compareAndSet(false, true)) startedLatch.countDown();
                if (result == null || result.getSentence() == null) return;
                String text = result.getSentence().getText();
                if (text == null) return;
                try {
                    log.debug("[DashScope-ASR] Received result: isSentenceEnd={}, text={}", result.isSentenceEnd(), text);
                    latestPartial.setLength(0);
                    latestPartial.append(text);
                    if (result.isSentenceEnd()) {
                        if (!text.isEmpty()) finalBuf.append(text);
                        cb.onPartial(text);
                    } else {
                        cb.onPartial(text);
                    }
                } catch (Exception ignore) {}
            }
            @Override
            public void onComplete() {
                if (closed.get()) return;
                if (!stopped.compareAndSet(false, true)) return;
                String text = finalBuf.toString().trim();
                if (text.isBlank()) {
                    text = latestPartial.toString().trim();
                }
                log.info("[DashScope-ASR] Recognition completed, final text: {}", text);
                try { cb.onFinal(text); } catch (Exception e) { cb.onError(e); }
            }
            @Override
            public void onError(Exception e) {
                if (closed.get()) return;
                if (!stopped.compareAndSet(false, true)) return;
                log.warn("[DashScope-ASR] callback error: {}", e.getMessage(), e);
                try { cb.onError(e); } catch (Exception ignore) {}
            }
        };

        // 异步打开 WebSocket 会话；call 方法本身可能阻塞短时间
        executor.execute(() -> {
            try {
                String asrModel = voiceProps.getDashscope() != null && voiceProps.getDashscope().getAsrModel() != null
                        && !voiceProps.getDashscope().getAsrModel().isBlank()
                        ? voiceProps.getDashscope().getAsrModel()
                        : "paraformer-realtime-v2";
                log.info("[DashScope-ASR] Opening session with model: {}, sampleRate: {}", asrModel, sampleRate);
                RecognitionParam param = RecognitionParam.builder()
                        .apiKey(apiKey)
                        .model(asrModel)
                        .format("pcm")
                        .sampleRate(sampleRate)
                        .build();
                recognizer.call(param, resultCb);
                log.info("[DashScope-ASR] Session opened successfully, WebSocket connected");
                if (started.compareAndSet(false, true)) startedLatch.countDown();

                // 短暂等待确保WebSocket完全就绪
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

                // 重放在握手期间被缓冲的音频
                byte[] frame;
                int replayCount = 0;
                int replayBytes = 0;
                while ((frame = pending.poll()) != null) {
                    if (closed.get()) break;
                    try {
                        recognizer.sendAudioFrame(ByteBuffer.wrap(frame));
                        replayCount++;
                        replayBytes += frame.length;
                    }
                    catch (Exception ex) {
                        log.warn("[DashScope-ASR] replay frame failed: {}", ex.getMessage());
                    }
                }
                if (replayCount > 0) {
                    log.info("[DashScope-ASR] Replayed {} buffered frames ({} bytes)", replayCount, replayBytes);
                }
            } catch (Exception e) {
                log.error("[DashScope-ASR] open failed: {}", e.getMessage(), e);
                startedLatch.countDown();
                if (stopped.compareAndSet(false, true)) {
                    try { cb.onError(e); } catch (Exception ignore) {}
                }
            }
        });

        return new AsrSession() {
            @Override
            public void sendAudio(byte[] pcm) {
                if (closed.get() || pcm == null || pcm.length == 0) return;
                if (!started.get()) {
                    // recognizer 还在握手，先缓冲；call() 返回后由打开线程重放
                    pending.offer(pcm);
                    return;
                }
                try {
                    ByteBuffer buffer = ByteBuffer.wrap(pcm);
                    recognizer.sendAudioFrame(buffer);
                    log.debug("[DashScope-ASR] Sent audio frame: {} bytes", pcm.length);
                } catch (Exception e) {
                    log.warn("[DashScope-ASR] sendAudio failed: {}", e.getMessage(), e);
                    // 如果发送失败，可能是连接已断开，触发错误回调
                    if (e.getMessage() != null && (e.getMessage().contains("closed") ||
                        e.getMessage().contains("Sending streaming data exception") ||
                        e.getMessage().contains("State invalid"))) {
                        if (stopped.compareAndSet(false, true)) {
                            try { cb.onError(e); } catch (Exception ignore) {}
                        }
                    }
                }
            }

            @Override
            public void endOfStream() {
                if (closed.get()) return;
                log.info("[DashScope-ASR] endOfStream called, waiting for recognizer to be ready...");
                // 等 recognizer 真正握手完成，避免 SDK 抛 "State invalid" 后 onComplete 丢失，前端卡死
                try {
                    if (!startedLatch.await(5, TimeUnit.SECONDS)) {
                        log.warn("[DashScope-ASR] Recognizer not ready after 5s, forcing final callback");
                        if (stopped.compareAndSet(false, true)) {
                            try { cb.onFinal(""); } catch (Exception ignore) {}
                        }
                        safeCloseDuplex(recognizer);
                        return;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    log.info("[DashScope-ASR] Calling recognizer.stop()");
                    recognizer.stop();
                    log.info("[DashScope-ASR] recognizer.stop() completed");
                } catch (Exception e) {
                    log.warn("[DashScope-ASR] stop failed: {}", e.getMessage(), e);
                    // stop 失败时 SDK 不会再触发 onComplete，必须手动兜底 onFinal，否则前端永远停在 THINKING
                    if (stopped.compareAndSet(false, true)) {
                        try { cb.onFinal(finalBuf.toString().trim()); } catch (Exception ignore) {}
                    }
                    safeCloseDuplex(recognizer);
                }
            }

            @Override
            public void cancel() {
                if (!closed.compareAndSet(false, true)) return;
                pending.clear();
                safeCloseDuplex(recognizer);
            }
        };
    }

    private static void safeCloseDuplex(Recognition recognizer) {
        try {
            if (recognizer.getDuplexApi() != null) {
                recognizer.getDuplexApi().close(1000, "client cancel");
            }
        } catch (Exception ignore) {}
    }

    private AsrSession noopSession() {
        return new AsrSession() {
            @Override public void sendAudio(byte[] pcm) {}
            @Override public void endOfStream() {}
            @Override public void cancel() {}
        };
    }
}
