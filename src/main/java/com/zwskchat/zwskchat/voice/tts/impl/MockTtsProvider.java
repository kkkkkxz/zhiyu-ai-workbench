package com.zwskchat.zwskchat.voice.tts.impl;

import com.zwskchat.zwskchat.voice.tts.TtsCallback;
import com.zwskchat.zwskchat.voice.tts.TtsProvider;
import com.zwskchat.zwskchat.voice.tts.TtsTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock TTS：根据文本长度推送 N 帧"占位音频"（PCM 16kHz 静音），供前端验证
 * TTS_BEGIN / 音频帧 / TTS_END 完整流程。生产请切换到真实 TTS。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockTtsProvider implements TtsProvider {

    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;

    @Override public String name() { return "mock"; }
    @Override public String audioFormat() { return "pcm-16khz-16bit-mono"; }

    @Override
    public TtsTask synthesize(String text, TtsCallback cb) {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean done = new AtomicBoolean();
        executor.execute(() -> {
            try {
                int frames = Math.max(1, Math.min(20, text.length() / 4 + 1));
                int frameMs = 200;
                int bytesPerFrame = 16000 * 2 * frameMs / 1000;
                byte[] silence = new byte[bytesPerFrame]; // 全 0 = 静音
                for (int i = 0; i < frames; i++) {
                    if (cancelled.get()) return;
                    cb.onAudio(silence);
                    Thread.sleep(frameMs);
                }
                if (!cancelled.get()) cb.onComplete();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (!cancelled.get()) cb.onError(e);
            } finally {
                done.set(true);
            }
        });
        return new TtsTask() {
            @Override public void cancel() { cancelled.set(true); }
            @Override public boolean isDone() { return done.get(); }
        };
    }
}
