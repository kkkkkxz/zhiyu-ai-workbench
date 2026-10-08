package com.zwskchat.zwskchat.voice.asr.impl;

import com.zwskchat.zwskchat.voice.asr.AsrCallback;
import com.zwskchat.zwskchat.voice.asr.AsrProvider;
import com.zwskchat.zwskchat.voice.asr.AsrSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock ASR：不调用任何外部服务，根据收到的音频字节数生成一句固定文本，
 * 仅用于本地联调端到端语音流水线。生产请切换到真实 ASR。
 */
@Slf4j
@Component
public class MockAsrProvider implements AsrProvider {

    @Override
    public String name() { return "mock"; }

    @Override
    public AsrSession open(int sampleRate, AsrCallback cb) {
        return new AsrSession() {
            private final AtomicInteger bytes = new AtomicInteger();
            private final AtomicBoolean done = new AtomicBoolean();

            @Override
            public void sendAudio(byte[] pcm) {
                if (done.get() || pcm == null) return;
                bytes.addAndGet(pcm.length);
            }

            @Override
            public void endOfStream() {
                if (!done.compareAndSet(false, true)) return;
                int kb = Math.max(1, bytes.get() / 1024);
                String text = "你好，这是 MOCK ASR 识别到的内容（约 " + kb + " KB 音频）。";
                try { cb.onFinal(text); } catch (Exception e) { cb.onError(e); }
            }

            @Override
            public void cancel() {
                done.set(true);
            }
        };
    }
}
