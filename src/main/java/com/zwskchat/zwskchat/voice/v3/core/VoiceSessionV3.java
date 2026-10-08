package com.zwskchat.zwskchat.voice.v3.core;

import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.voice.asr.AsrSession;
import com.zwskchat.zwskchat.voice.tts.TtsTask;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceModeV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceStateV3;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Getter
public class VoiceSessionV3 {
    private final String connectionId;
    private final WebSocketSession ws;
    private final Long userId;
    private final Long tenantId;
    private final String username;
    private final boolean publicJarvis;
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastActivityAt = System.currentTimeMillis();

    private final AtomicReference<VoiceStateV3> state = new AtomicReference<>(VoiceStateV3.IDLE);
    private final AtomicLong turnSeq = new AtomicLong();

    private volatile Long aiSessionId;
    private volatile List<Long> kbIds;
    private volatile String personaId;
    private volatile VoiceModeV3 mode = VoiceModeV3.HANDS_FREE;
    private volatile String currentTurnId;
    /** 端侧（如 Porcupine）是否已完成唤醒；true 时服务端跳过唤醒词二次校验。逐轮重置。 */
    private volatile boolean wakeVerified;
    private volatile AsrSession asrSession;
    private final AtomicReference<TtsTask> currentTts = new AtomicReference<>();
    private final AtomicReference<Future<?>> currentLlm = new AtomicReference<>();
    /**
     * 当前 turn 的 TTS 串行队列消费 worker。
     * <p>开启"流式分句 + 边生成边 TTS"优化后，LLM 主线程把每一句送入队列，
     * 由独立 worker 串行调 speakOne。INTERRUPT / 状态切换时必须把它一起取消，
     * 否则即便 LLM future 已 cancel、TtsTask 已 cancel，worker 仍可能从队列里
     * 摸出下一句继续合成，导致"挂断后 AI 还在说"。</p>
     */
    private final AtomicReference<Future<?>> currentTtsWorker = new AtomicReference<>();

    private final AtomicLong audioFrames = new AtomicLong();
    private final AtomicLong audioBytes = new AtomicLong();
    private volatile int maxPeak;
    private volatile long vadTurnStartedAtMs = System.currentTimeMillis();
    private volatile long vadLastSpeechAtMs = vadTurnStartedAtMs;
    private volatile boolean vadHasSpeech;
    private final AtomicBoolean vadAutoEnding = new AtomicBoolean(false);
    /** 自适应底噪：本轮校准期内的 peak 上界（取最大值，保守一点不容易把语音漏掉）。 */
    private volatile int vadNoisePeak;
    /** 自适应底噪：本轮校准期内的 rms 上界。 */
    private volatile int vadNoiseRms;
    /** 是否完成本轮底噪校准。 */
    private volatile boolean vadCalibrated;
    /** 滑动窗口：bit 0..(N-1) 表示最近 N 帧是否达标。 */
    private volatile long vadWindowBits;
    /** 滑动窗口当前有效帧数（未填满时小于 N）。 */
    private volatile int vadWindowSize;

    /**
     * 匿名 Jarvis 单连接消息预算计数器。
     * <p>仅当 {@link #publicJarvis} 为 true 时有意义；每次 ASR_FINAL 进入推理前递增，
     * 超过 {@link #publicJarvisMaxMessages} 后服务端主动断开 WS。</p>
     */
    private final AtomicInteger publicJarvisMessageCount = new AtomicInteger(0);
    /** 单连接消息上限；0 或负数 = 不限制（仅鉴权用户）。 */
    private final int publicJarvisMaxMessages;

    public VoiceSessionV3(WebSocketSession ws, Long userId, Long tenantId, String username,
                          boolean publicJarvis, int publicJarvisMaxMessages) {
        this.connectionId = ws.getId();
        this.ws = ws;
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.publicJarvis = publicJarvis;
        this.publicJarvisMaxMessages = publicJarvisMaxMessages;
    }

    /** 旧 5 参数构造保留以避免破坏调用方；非 public-jarvis 路径走此入口。 */
    public VoiceSessionV3(WebSocketSession ws, Long userId, Long tenantId, String username,
                          boolean publicJarvis) {
        this(ws, userId, tenantId, username, publicJarvis, 0);
    }

    /**
     * 递增匿名消息计数并判断是否超出预算。
     *
     * @return true 表示已超出预算，调用方应主动 close WS；false 表示仍在预算内可继续
     */
    public boolean publicJarvisExceededBudget() {
        if (!publicJarvis || publicJarvisMaxMessages <= 0) return false;
        int n = publicJarvisMessageCount.incrementAndGet();
        return n > publicJarvisMaxMessages;
    }

    public int getPublicJarvisMessageCount() {
        return publicJarvisMessageCount.get();
    }

    public String nextTurnId() {
        String id = connectionId + "-" + turnSeq.incrementAndGet();
        currentTurnId = id;
        audioFrames.set(0);
        audioBytes.set(0);
        maxPeak = 0;
        long now = System.currentTimeMillis();
        vadTurnStartedAtMs = now;
        vadLastSpeechAtMs = now;
        vadHasSpeech = false;
        vadAutoEnding.set(false);
        vadNoisePeak = 0;
        vadNoiseRms = 0;
        vadCalibrated = false;
        vadWindowBits = 0L;
        vadWindowSize = 0;
        touch();
        return id;
    }

    public void forceState(VoiceStateV3 next) {
        VoiceStateV3 prev = state.getAndSet(next);
        touch();
        if (prev != next) {
            log.info("[Voice-V3] state {} -> {}, conn={}, turn={}", prev, next, connectionId, currentTurnId);
        }
    }

    /**
     * 原子地把状态从 {@code expect} 切到 {@code next}。
     * <p>用于解决 onAudio (WS IO 线程) / onFinal (ASR 回调线程) / endAudio 之间
     * 的并发竞态，例如前端 AUDIO_END 与服务端 VAD 自动 end 同时触发时，
     * 只有一方能成功 LISTENING → THINKING，避免重复进入推理流水线。</p>
     *
     * @return true 当且仅当此次成功切换；false 表示当前状态不是 {@code expect}
     */
    public boolean transitionState(VoiceStateV3 expect, VoiceStateV3 next) {
        if (!state.compareAndSet(expect, next)) return false;
        touch();
        log.info("[Voice-V3] state {} -> {} (CAS), conn={}, turn={}", expect, next, connectionId, currentTurnId);
        return true;
    }

    public VoiceStateV3 state() {
        return state.get();
    }

    public void touch() {
        lastActivityAt = System.currentTimeMillis();
    }

    public void addAudioFrame(byte[] audio, int peak) {
        if (audio != null) {
            audioFrames.incrementAndGet();
            audioBytes.addAndGet(audio.length);
        }
        if (peak > maxPeak) maxPeak = peak;
        touch();
    }

    public void setAiSessionId(Long aiSessionId) {
        this.aiSessionId = aiSessionId;
        touch();
    }

    public void setKbIds(List<Long> kbIds) {
        this.kbIds = kbIds;
        touch();
    }

    public void setPersonaId(String personaId) {
        this.personaId = personaId;
        touch();
    }

    public void setMode(VoiceModeV3 mode) {
        this.mode = mode == null ? VoiceModeV3.HANDS_FREE : mode;
        touch();
    }

    public void setWakeVerified(boolean wakeVerified) {
        this.wakeVerified = wakeVerified;
    }

    public void setAsrSession(AsrSession asrSession) {
        this.asrSession = asrSession;
        touch();
    }

    /**
     * 服务端 VAD 兜底（HANDS_FREE）。
     *
     * <p>新版三层保护，专为嘈杂环境（持续高底噪导致单帧阈值永久达标）设计：</p>
     * <ol>
     *   <li><b>底噪校准</b>：每轮起手 {@code noiseCalibrationMs} 毫秒只采底噪、不判语音，
     *       取该窗口内 peak/rms 的最大值作为本轮噪声底；</li>
     *   <li><b>自适应阈值 + 滑动窗口</b>：实际阈值 = max(静态阈值, 底噪×倍率)；
     *       且必须在最近 {@code windowFrames} 帧内有 {@code windowHits} 帧达标才算"有语音"，
     *       单帧瞬时尖峰（鼠标键盘、咳嗽尾巴）不会锁死本轮；</li>
     *   <li><b>maxTurnMs 硬上限</b>：累计录音超过该值无条件结束，确保再嘈杂的环境也能退出 LISTENING。</li>
     * </ol>
     *
     * @return true = 应当立即结束当轮（auto endAudio）；false = 继续接收音频
     */
    public boolean observeServerVad(int peak, int rms, VoiceProperties.ServerVad cfg) {
        long now = System.currentTimeMillis();
        long elapsed = now - vadTurnStartedAtMs;

        // 硬上限：噪声环境下哪怕一直"判定有语音"也必须结束
        if (cfg.getMaxTurnMs() > 0 && elapsed >= cfg.getMaxTurnMs()) {
            return vadAutoEnding.compareAndSet(false, true);
        }

        // 校准期：只采底噪，不做语音判定
        if (!vadCalibrated) {
            if (peak > vadNoisePeak) vadNoisePeak = peak;
            if (rms > vadNoiseRms) vadNoiseRms = rms;
            if (elapsed >= Math.max(0, cfg.getNoiseCalibrationMs())) {
                vadCalibrated = true;
            }
            return false;
        }

        // 自适应阈值：环境底噪越大，越要"明显大于底噪"才认为是语音；
        // clamp 一层防止误把已经在说话时启动算成底噪、导致后续完全判不出语音。
        int dynPeak = Math.max(cfg.getSpeechPeak(),
                (int) Math.min(cfg.getSpeechPeak() * 4.0, vadNoisePeak * cfg.getNoisePeakMultiplier()));
        int dynRms = Math.max(cfg.getSpeechRms(),
                (int) Math.min(cfg.getSpeechRms() * 4.0, vadNoiseRms * cfg.getNoiseRmsMultiplier()));
        boolean frameSpeech = peak >= dynPeak || rms >= dynRms;

        // 滑动窗口：单帧不算数，最近 N 帧累计达标 >= K 才标记为正在说话
        int win = Math.max(1, Math.min(64, cfg.getSpeechWindowFrames()));
        int hitsRequired = Math.max(1, Math.min(win, cfg.getSpeechWindowHits()));
        long mask = win >= 64 ? -1L : ((1L << win) - 1L);
        vadWindowBits = ((vadWindowBits << 1) | (frameSpeech ? 1L : 0L)) & mask;
        if (vadWindowSize < win) vadWindowSize++;
        int hits = Long.bitCount(vadWindowBits);

        if (hits >= hitsRequired) {
            vadHasSpeech = true;
            vadLastSpeechAtMs = now;
            return false;
        }
        return vadHasSpeech
                && elapsed >= cfg.getMinRecordingMs()
                && now - vadLastSpeechAtMs >= cfg.getSilenceMs()
                && vadAutoEnding.compareAndSet(false, true);
    }
}
