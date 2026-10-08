package com.zwskchat.zwskchat.voice.v3.orchestrator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiMessageReference;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.chat.mapper.AiMessageReferenceMapper;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.config.properties.VoiceProperties;
import com.zwskchat.zwskchat.kb.entity.KnowledgeBase;
import com.zwskchat.zwskchat.kb.mapper.KnowledgeBaseMapper;
import com.zwskchat.zwskchat.persona.service.PersonaService;
import com.zwskchat.zwskchat.rag.RagService;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import com.zwskchat.zwskchat.safety.ContentSafetyService;
import com.zwskchat.zwskchat.stock.dto.StockDialogueAnswer;
import com.zwskchat.zwskchat.stock.service.StockDialogueService;
import com.zwskchat.zwskchat.tenant.TenantContext;
import com.zwskchat.zwskchat.voice.asr.AsrCallback;
import com.zwskchat.zwskchat.voice.asr.AsrRouter;
import com.zwskchat.zwskchat.voice.asr.AsrSession;
import com.zwskchat.zwskchat.voice.service.SentenceSplitter;
import com.zwskchat.zwskchat.voice.tts.TtsCallback;
import com.zwskchat.zwskchat.voice.tts.TtsRouter;
import com.zwskchat.zwskchat.voice.tts.TtsTask;
import com.zwskchat.zwskchat.voice.v3.core.VoiceSessionV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceClientMessageV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceModeV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceServerMessageV3;
import com.zwskchat.zwskchat.voice.v3.protocol.VoiceStateV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceOrchestratorV3 {
    private static final String INPUT_AUDIO_FORMAT = "pcm-16khz-16bit-mono";
    private static final Pattern SPEAKABLE_TEXT = Pattern.compile("[\\p{IsHan}\\p{L}\\p{N}]");
    // 服务端 VAD 兜底参数全部走 VoiceProperties.serverVad，见 application.yaml `zwsk.voice.server-vad.*`。
    // 具体自适应逻辑在 VoiceSessionV3.observeServerVad，专门解决嘈杂环境 LISTENING 卡死问题。

    private final ObjectMapper om;
    private final AsrRouter asrRouter;
    private final TtsRouter ttsRouter;
    private final RagService ragService;
    private final AiChatClient chatClient;
    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AiMessageReferenceMapper referenceMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final VoiceProperties voiceProps;
    private final ContentSafetyService contentSafety;
    private final StockDialogueService stockDialogueService;
    private final PersonaService personaService;
    private final com.zwskchat.zwskchat.voice.service.WakeWordDetector wakeWordDetector;

    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;

    public void onConnected(VoiceSessionV3 session) {
        send(session, VoiceServerMessageV3.ready(session.getConnectionId(), INPUT_AUDIO_FORMAT));
        setState(session, VoiceStateV3.IDLE);
    }

    public void onClientMessage(VoiceSessionV3 session, VoiceClientMessageV3 msg) {
        String type = msg.getType() == null ? "" : msg.getType().trim().toUpperCase();
        switch (type) {
            case "HELLO" -> send(session, VoiceServerMessageV3.ready(session.getConnectionId(), INPUT_AUDIO_FORMAT));
            case "START_TURN", "START" -> startTurn(session, msg);
            case "AUDIO_END", "END", "STOP" -> endAudio(session);
            case "INTERRUPT" -> interrupt(session);
            case "RESET" -> reset(session);
            case "PING" -> send(session, VoiceServerMessageV3.pong());
            default -> send(session, VoiceServerMessageV3.error(1001, "未知 V3 消息类型: " + type, true));
        }
    }

    public void startTurn(VoiceSessionV3 session, VoiceClientMessageV3 msg) {
        try {
            VoiceModeV3 mode = parseMode(msg.getMode());
            session.setMode(mode);
            session.setPersonaId(msg.getPersonaId());
            // 端侧（如 Porcupine）唤醒标记：START_TURN.client.wakeVerified=true 时跳过服务端唤醒词二次校验。
            session.setWakeVerified(isClientWakeVerified(msg));
            String turnId = session.nextTurnId();
            resolveBusinessSession(session, msg);
            send(session, VoiceServerMessageV3.session(session.getAiSessionId()));
            cancelInflight(session);

            if (mode == VoiceModeV3.VISUAL) {
                log.info("[Voice-V3] visual mode starts ASR only conn={}, turn={}", session.getConnectionId(), turnId);
            }

            AsrSession asr = asrRouter.get().open(
                    msg.getSampleRate() == null || msg.getSampleRate() <= 0 ? voiceProps.getSampleRate() : msg.getSampleRate(),
                    buildAsrCallback(session, turnId, mode));
            session.setAsrSession(asr);
            setState(session, VoiceStateV3.LISTENING);
            log.info("[Voice-V3] turn started conn={}, turn={}, mode={}, aiSession={}",
                    session.getConnectionId(), turnId, mode, session.getAiSessionId());
        } catch (Exception e) {
            log.warn("[Voice-V3] start turn failed conn={}: {}", session.getConnectionId(), e.getMessage(), e);
            send(session, VoiceServerMessageV3.error(6001, "语音会话启动失败：" + safeMsg(e), true));
            setState(session, VoiceStateV3.IDLE);
        }
    }

    public void onAudio(VoiceSessionV3 session, byte[] pcm) {
        if (session.state() != VoiceStateV3.LISTENING) return;
        int peak = peak16(pcm);
        int rms = rms16(pcm);
        session.addAudioFrame(pcm, peak);
        AsrSession asr = session.getAsrSession();
        if (asr != null) {
            asr.sendAudio(pcm);
        }
        if (session.getMode() == VoiceModeV3.HANDS_FREE
                && session.observeServerVad(peak, rms, voiceProps.getServerVad())) {
            log.info("[Voice-V3] server auto end conn={}, turn={}, frames={}, peak={}, rms={}, noisePeak={}, noiseRms={}, elapsedMs={}",
                    session.getConnectionId(), session.getCurrentTurnId(), session.getAudioFrames().get(),
                    peak, rms, session.getVadNoisePeak(), session.getVadNoiseRms(),
                    System.currentTimeMillis() - session.getVadTurnStartedAtMs());
            endAudio(session);
        }
    }

    public void endAudio(VoiceSessionV3 session) {
        // 用 CAS 原子地把 LISTENING → THINKING：前端 AUDIO_END 和服务端 VAD 自动 end 可能并发触发，
        // 只有第一个调用方能成功切换状态、推进流水线；后续调用 transitionState 返回 false 直接返回，
        // 避免重复 endOfStream / 重复 setState 引发的 UI 抖动。
        if (!session.transitionState(VoiceStateV3.LISTENING, VoiceStateV3.THINKING)) return;
        String turnId = session.getCurrentTurnId();
        AsrSession asr = session.getAsrSession();
        if (asr == null || session.getAudioFrames().get() == 0) {
            // CAS 已经把状态切到 THINKING，但其实没有有效音频。把状态转回 IDLE 并发空转 turn。
            session.forceState(VoiceStateV3.LISTENING); // 让 gracefulEmptyTurn 走它原本的 IDLE 路径
            gracefulEmptyTurn(session, turnId, "NO_AUDIO_FRAME");
            return;
        }
        log.info("[Voice-V3] audio end conn={}, turn={}, frames={}, bytes={}, peak={}",
                session.getConnectionId(), turnId, session.getAudioFrames().get(), session.getAudioBytes().get(), session.getMaxPeak());
        // 已经在 CAS 中切到 THINKING 了，此处只补一次 send STATE 通知前端
        send(session, VoiceServerMessageV3.state(VoiceStateV3.THINKING));
        try {
            asr.endOfStream();
        } catch (Exception e) {
            log.warn("[Voice-V3] ASR end failed conn={}, turn={}: {}", session.getConnectionId(), turnId, e.getMessage(), e);
            if (isNoValidAudio(e)) {
                gracefulEmptyTurn(session, turnId, "NO_VALID_AUDIO");
            } else {
                send(session, VoiceServerMessageV3.error(6002, "ASR 结束失败：" + safeMsg(e), true));
                setState(session, VoiceStateV3.IDLE);
            }
        }
    }

    public void interrupt(VoiceSessionV3 session) {
        log.info("[Voice-V3] interrupt conn={}, turn={}", session.getConnectionId(), session.getCurrentTurnId());
        cancelInflight(session);
        setState(session, VoiceStateV3.IDLE);
        send(session, VoiceServerMessageV3.turnEnd(session.getCurrentTurnId(), "INTERRUPTED"));
    }

    public void reset(VoiceSessionV3 session) {
        cancelInflight(session);
        session.setAiSessionId(null);
        session.setKbIds(null);
        session.setPersonaId(null);
        setState(session, VoiceStateV3.IDLE);
    }

    public void close(VoiceSessionV3 session) {
        cancelInflight(session);
        session.forceState(VoiceStateV3.CLOSED);
    }

    private AsrCallback buildAsrCallback(VoiceSessionV3 session, String turnId, VoiceModeV3 mode) {
        return new AsrCallback() {
            @Override
            public void onPartial(String text) {
                if (!isCurrentTurnActive(session, turnId)) return;
                send(session, VoiceServerMessageV3.asrPartial(turnId, text == null ? "" : text));
            }

            @Override
            public void onFinal(String text) {
                if (!isCurrentTurnActive(session, turnId)) {
                    log.debug("[Voice-V3] ignore stale ASR final conn={}, turn={}", session.getConnectionId(), turnId);
                    return;
                }
                String finalText = text == null ? "" : text.trim();
                log.info("[Voice-V3] ASR final conn={}, turn={}, text='{}'", session.getConnectionId(), turnId, finalText);
                send(session, VoiceServerMessageV3.asrFinal(turnId, finalText));
                session.setAsrSession(null);
                if (finalText.isBlank()) {
                    gracefulEmptyTurn(session, turnId, "EMPTY_TRANSCRIPT");
                    return;
                }
                if (mode == VoiceModeV3.VISUAL) {
                    setState(session, VoiceStateV3.IDLE);
                    send(session, VoiceServerMessageV3.turnEnd(turnId, "VISUAL_DONE"));
                    return;
                }
                // 唤醒词门禁（对应工程计划书“唤醒词层独立”）：仅 HANDS_FREE 连续对话校验，
                // 端侧已唤醒（wakeVerified）或未启用时直接放行；命中后剥离唤醒词再送 LLM。
                String effectiveText = applyWakeWordGate(session, turnId, mode, finalText);
                if (effectiveText == null) {
                    return; // 未命中唤醒词，已结束该轮
                }
                runRagLlmTts(session, turnId, effectiveText);
            }

            @Override
            public void onError(Throwable t) {
                if (!isCurrentTurnActive(session, turnId)) return;
                session.setAsrSession(null);
                if (isNoValidAudio(t)) {
                    log.info("[Voice-V3] ASR no valid audio conn={}, turn={}: {}", session.getConnectionId(), turnId, safeMsg(t));
                    gracefulEmptyTurn(session, turnId, "NO_VALID_AUDIO");
                    return;
                }
                log.warn("[Voice-V3] ASR error conn={}, turn={}: {}", session.getConnectionId(), turnId, safeMsg(t), t);
                send(session, VoiceServerMessageV3.error(6002, "ASR 失败：" + safeMsg(t), true));
                setState(session, VoiceStateV3.IDLE);
                send(session, VoiceServerMessageV3.turnEnd(turnId, "ASR_ERROR"));
            }
        };
    }

    private boolean isCurrentTurnActive(VoiceSessionV3 session, String turnId) {
        return session != null
                && (session.state() == VoiceStateV3.LISTENING || session.state() == VoiceStateV3.THINKING)
                && turnId != null
                && turnId.equals(session.getCurrentTurnId());
    }

    /**
     * 读取 START_TURN.client.wakeVerified（端侧 Porcupine 等已完成唤醒）。
     */
    private boolean isClientWakeVerified(VoiceClientMessageV3 msg) {
        if (msg == null || msg.getClient() == null) return false;
        Object v = msg.getClient().get("wakeVerified");
        if (v instanceof Boolean b) return b;
        return v != null && "true".equalsIgnoreCase(v.toString());
    }

    /**
     * 唤醒词门禁：返回送入 LLM 的有效文本；未命中唤醒词时结束该轮并返回 null。
     *
     * <p>放行条件（任一）：未启用唤醒词校验；非 HANDS_FREE 且配置 handsFreeOnly；
     * 端侧已唤醒（wakeVerified）。否则要求本轮文本以唤醒词开头，命中后按配置剥离前缀。</p>
     */
    private String applyWakeWordGate(VoiceSessionV3 session, String turnId, VoiceModeV3 mode, String finalText) {
        VoiceProperties.WakeWord cfg = voiceProps.getWakeWord();
        if (cfg == null || !cfg.isEnabled()) return finalText;
        if (cfg.isHandsFreeOnly() && mode != VoiceModeV3.HANDS_FREE) return finalText;
        if (session.isWakeVerified()) return finalText;

        com.zwskchat.zwskchat.voice.service.WakeWordDetector.Result r = wakeWordDetector.detect(finalText, cfg);
        if (r.awake()) {
            log.info("[Voice-V3] wake word matched conn={}, turn={}, phrase='{}'",
                    session.getConnectionId(), turnId, r.matchedPhrase());
            String stripped = r.strippedText();
            // 仅说了唤醒词、没有正文（如“你好展望”）：不进 LLM，回到待命，等待下一句指令。
            if (stripped == null || stripped.isBlank()) {
                setState(session, VoiceStateV3.IDLE);
                send(session, VoiceServerMessageV3.turnEnd(turnId, "WAKE_ONLY"));
                return null;
            }
            return stripped;
        }

        log.info("[Voice-V3] wake word missing conn={}, turn={}, text='{}'",
                session.getConnectionId(), turnId, finalText);
        if (cfg.getMissHint() != null && !cfg.getMissHint().isBlank()) {
            send(session, VoiceServerMessageV3.llmDelta(turnId, cfg.getMissHint()));
        }
        setState(session, VoiceStateV3.IDLE);
        send(session, VoiceServerMessageV3.turnEnd(turnId, "WAKE_REQUIRED"));
        return null;
    }

    private void runRagLlmTts(VoiceSessionV3 session, String turnId, String userText) {
        // 匿名 Jarvis 预算守卫：每个 ASR_FINAL → LLM/RAG/TTS 触发前先检查单连接消息上限。
        // 防御目标：即使握手通过了 IP 限流，也防止单条长连接持续抽水（如 keep-alive
        // 重连无效后改为单连接连续发问）。超出预算后给客户端 4290 业务错误，并主动关 WS——
        // 客户端如要继续，只能重新建连，又会再次撞 IP 限流。
        if (session.publicJarvisExceededBudget()) {
            log.warn("[Voice-V3] public-jarvis budget exceeded conn={}, count={}",
                    session.getConnectionId(), session.getPublicJarvisMessageCount());
            send(session, VoiceServerMessageV3.error(4290,
                    "免登录通话已达单次连接消息上限，请稍后重新进入语音", true));
            try {
                session.getWs().close(CloseStatus.POLICY_VIOLATION.withReason("public-jarvis budget exhausted"));
            } catch (Exception ignored) {}
            return;
        }

        var future = executor.submit(() -> {
            try (TenantContext.Scope tenantScope = TenantContext.withTenant(session.getTenantId())) {
                long t0 = System.currentTimeMillis();
                AiMessage userMsg = newMsg(session, "user", userText);
                withTenant(session, () -> aiMessageMapper.insert(userMsg));
                updateVoiceSession(session, userText);

                Optional<StockDialogueAnswer> stockAnswer = stockDialogueService.tryAnswer(userText, true);
                if (stockAnswer.isPresent()) {
                    String answer = stockAnswer.get().text();
                    ContentSafetyService.SafetyResult safety = contentSafety.check(answer);
                    String safeAnswer = safety.safe() ? answer : safety.sanitized();
                    send(session, VoiceServerMessageV3.references(turnId, List.of()));
                    send(session, VoiceServerMessageV3.llmDelta(turnId, safeAnswer));
                    if (isSpeakableText(safeAnswer)) {
                        speakOne(session, turnId, safeAnswer);
                    }

                    AiMessage assistant = newMsg(session, "assistant", safeAnswer);
                    assistant.setLatencyMs((int) Math.min(System.currentTimeMillis() - t0, Integer.MAX_VALUE));
                    withTenant(session, () -> aiMessageMapper.insert(assistant));
                    send(session, VoiceServerMessageV3.llmDone(turnId, assistant.getId()));

                    if (session.state() != VoiceStateV3.CLOSED) {
                        setState(session, VoiceStateV3.IDLE);
                        send(session, VoiceServerMessageV3.turnEnd(turnId, "DONE"));
                    }
                    return;
                }

                long retrieveStart = System.currentTimeMillis();
                List<RetrievedChunk> retrievedChunks = withTenantCall(session, () ->
                        ragService.retrieve(session.getKbIds() == null ? List.of() : session.getKbIds(), userText));
                boolean explicitRagMatch = ragService.hasExplicitVoiceMatch(retrievedChunks);
                List<RetrievedChunk> chunks = explicitRagMatch ? retrievedChunks : List.of();
                double bestRagScore = ragService.bestScore(retrievedChunks);
                log.info("[Voice-V3] RAG retrieved conn={}, turn={}, kbIds={}, hits={}, explicit={}, bestScore={}, costMs={}",
                        session.getConnectionId(), turnId,
                        session.getKbIds() == null ? 0 : session.getKbIds().size(),
                        retrievedChunks == null ? 0 : retrievedChunks.size(),
                        explicitRagMatch,
                        String.format("%.6f", bestRagScore),
                        System.currentTimeMillis() - retrieveStart);
                if (!explicitRagMatch && retrievedChunks != null && !retrievedChunks.isEmpty()) {
                    log.info("[Voice-V3] RAG weak/no explicit match conn={}, turn={}, bestScore={} → fallback to general LLM/TTS",
                            session.getConnectionId(), turnId, String.format("%.6f", bestRagScore));
                }
                send(session, VoiceServerMessageV3.references(turnId, toRefView(chunks)));

                // ─────────────────────────────────────────────────────────────
                // 流式分句 + TTS 串行队列消费（首音延迟优化）
                // ─────────────────────────────────────────────────────────────
                // 旧实现：等 LLM 整段完成后再调一次 speakOne(全文)，用户从说完话
                // 到听到 AI 第一字大约要 6~15 秒（=LLM 全程 + TTS 启动 + 首帧）。
                //
                // 新实现拆成两条并行流水线：
                //   ① 主线程：LLM 流式 delta → SentenceSplitter 切句 → 入 BlockingQueue
                //   ② 后台 worker：从队列拿一句就 speakOne（串行，保证发音顺序）
                // LLM 还在生成第 N+1 句时，TTS 已经在朗读第 N 句，首音延迟降到
                // 「首句生成时间」+「TTS 首帧」≈ 1.5~3 秒。
                //
                // cancel 路径：cancelInflight 会同时 cancel 主 future、当前 TtsTask
                // 和这个 ttsWorker future，确保挂断后不会还有句子从队列里被合成发音。
                final SentenceSplitter splitter = new SentenceSplitter(voiceProps.getTtsMinSentenceChars());
                final String TTS_QUEUE_END = "__TTS_QUEUE_END__"; // 哨兵：标记队列后续不再有新句子
                final BlockingQueue<String> ttsQueue = new LinkedBlockingQueue<>();
                final Future<?> ttsWorker = executor.submit(() -> {
                    try (TenantContext.Scope workerTenantScope = TenantContext.withTenant(session.getTenantId())) {
                        while (!Thread.currentThread().isInterrupted()) {
                            String s;
                            try {
                                s = ttsQueue.poll(200, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            if (!isCurrentPipelineActive(session, turnId)) return;
                            if (s == null) continue;
                            if (TTS_QUEUE_END.equals(s)) return;
                            try {
                                speakOne(session, turnId, s);
                            } catch (Exception inner) {
                                if (isPipelineInterrupted(session, turnId, inner)) return;
                                // 单句失败（TTS 限流 / 单句无效字符等）不应该拖累整轮，
                                // 已在 speakOne 内部记录详细日志，这里只记一行兜底，继续消费队列。
                                log.warn("[Voice-V3] tts-worker speakOne failed conn={}, turn={}: {}",
                                        session.getConnectionId(), turnId, safeMsg(inner));
                            }
                        }
                    }
                });
                session.getCurrentTtsWorker().set(ttsWorker);

                List<ChatMessage> prompt = buildPrompt(session, userText, chunks, userMsg.getId());
                AtomicReference<AiChatClient.UsageInfo> usageRef = new AtomicReference<>();
                AtomicBoolean safetyBlocked = new AtomicBoolean(false);
                String full = chatClient.streamChat(prompt,
                        delta -> {
                            if (!isCurrentPipelineActive(session, turnId)) {
                                throw new CancellationException("__interrupted__");
                            }
                            if (safetyBlocked.get()) return;
                            for (String s : splitter.append(delta)) {
                                ContentSafetyService.SafetyResult sr = contentSafety.check(s);
                                String clean = sr.safe() ? s : sr.sanitized();
                                send(session, VoiceServerMessageV3.llmDelta(turnId, clean));
                                if (!sr.safe()) {
                                    safetyBlocked.set(true);
                                    return;
                                }
                                // ★ 关键改动：分句一出来立刻入队，TTS worker 立即开始合成，
                                //   前端尽快听到第一句。不再等整段 LLM 完成。
                                if (isSpeakableText(clean)) {
                                    ttsQueue.offer(clean);
                                }
                            }
                        },
                        usageRef::set);

                if (!isCurrentPipelineActive(session, turnId)) {
                    ttsQueue.offer(TTS_QUEUE_END);
                    throw new CancellationException("__interrupted__");
                }
                // LLM 完成后 flush splitter 拿到最后一段（很可能不带强终结符号）。
                if (!safetyBlocked.get()) {
                    String tail = splitter.flush();
                    if (tail != null && !tail.isBlank()) {
                        ContentSafetyService.SafetyResult sr = contentSafety.check(tail);
                        String clean = sr.safe() ? tail : sr.sanitized();
                        send(session, VoiceServerMessageV3.llmDelta(turnId, clean));
                        if (sr.safe() && isSpeakableText(clean)) {
                            ttsQueue.offer(clean);
                        }
                    }
                }
                // 入哨兵：worker 在排空队列后碰到这个值就 return。
                ttsQueue.offer(TTS_QUEUE_END);

                if (!isCurrentPipelineActive(session, turnId)) {
                    throw new CancellationException("__interrupted__");
                }
                // 落库 / llm-done 与 TTS worker 并行：worker 不依赖 DB，DB 写入耗时
                // 不应推迟"用户听到声音"的时间。
                // safeFull 语义与旧版一致：
                //   - 流式过程没命中 block 时，full（LLM 原始）= 流式 sanitize 拼接，省一次整段扫描；
                //   - 命中过 block 时对整段再做一次兜底 sanitize 后再落库。
                String safeFull;
                if (safetyBlocked.get()) {
                    ContentSafetyService.SafetyResult fullCheck = contentSafety.check(full == null ? "" : full);
                    safeFull = fullCheck.safe() ? full : fullCheck.sanitized();
                } else {
                    safeFull = full == null ? "" : full;
                }
                AiMessage assistant = newMsg(session, "assistant", safeFull);
                assistant.setLatencyMs((int) Math.min(System.currentTimeMillis() - t0, Integer.MAX_VALUE));
                AiChatClient.UsageInfo usage = usageRef.get();
                if (usage != null) {
                    assistant.setTokenIn(usage.promptTokens());
                    assistant.setTokenOut(usage.completionTokens());
                }
                withTenant(session, () -> aiMessageMapper.insert(assistant));
                persistReferences(session, assistant.getId(), chunks);
                send(session, VoiceServerMessageV3.llmDone(turnId, assistant.getId()));

                // 等 TTS worker 排空队列。期间监控 pipeline 取消信号（INTERRUPT / 挂断 / 状态切换），
                // 一旦失活立即 cancel worker，避免"挂断后还在朗读队列里剩下的句子"。
                while (!ttsWorker.isDone()) {
                    if (!isCurrentPipelineActive(session, turnId)) {
                        ttsWorker.cancel(true);
                        break;
                    }
                    try {
                        ttsWorker.get(200, TimeUnit.MILLISECONDS);
                        break;
                    } catch (java.util.concurrent.TimeoutException te) {
                        // 还没排空，继续等
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        ttsWorker.cancel(true);
                        break;
                    } catch (java.util.concurrent.ExecutionException ee) {
                        log.warn("[Voice-V3] tts-worker died conn={}, turn={}: {}",
                                session.getConnectionId(), turnId, safeMsg(ee.getCause()));
                        break;
                    }
                }
                session.getCurrentTtsWorker().compareAndSet(ttsWorker, null);

                if (session.state() != VoiceStateV3.CLOSED) {
                    setState(session, VoiceStateV3.IDLE);
                    send(session, VoiceServerMessageV3.turnEnd(turnId, "DONE"));
                }
            } catch (RuntimeException re) {
                if (isPipelineInterrupted(session, turnId, re)) {
                    finishInterruptedTurn(session, turnId);
                    return;
                }
                handlePipelineError(session, turnId, re);
            } catch (Exception e) {
                if (isPipelineInterrupted(session, turnId, e)) {
                    finishInterruptedTurn(session, turnId);
                    return;
                }
                handlePipelineError(session, turnId, e);
            }
        });
        session.getCurrentLlm().set(future);
    }

    private void speakOne(VoiceSessionV3 session, String turnId, String sentence) {
        if (!isCurrentPipelineActive(session, turnId)) return;
        String ttsText = normalizeTtsText(sentence);
        if (!isSpeakableText(ttsText)) return;
        ContentSafetyService.SafetyResult guard = contentSafety.check(ttsText);
        String safeSentence = normalizeTtsText(guard.safe() ? ttsText : guard.sanitized());
        if (!isSpeakableText(safeSentence)) return;
        var provider = ttsRouter.get();
        if (session.state() != VoiceStateV3.SPEAKING) {
            setState(session, VoiceStateV3.SPEAKING);
        }
        log.info("[Voice-V3] TTS begin conn={}, turn={}, provider={}, format={}, chars={}",
                session.getConnectionId(), turnId, provider.name(), provider.audioFormat(), safeSentence.length());
        send(session, VoiceServerMessageV3.ttsBegin(turnId, safeSentence, provider.audioFormat()));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong audioBytes = new AtomicLong();
        TtsTask task = provider.synthesize(safeSentence, new TtsCallback() {
            @Override public void onAudio(byte[] audio) {
                if (audio != null) audioBytes.addAndGet(audio.length);
                if (isCurrentPipelineActive(session, turnId)) sendBinary(session.getWs(), audio);
            }
            @Override public void onComplete() { latch.countDown(); }
            @Override public void onError(Throwable t) {
                if (!isCurrentPipelineActive(session, turnId)) {
                    latch.countDown();
                    return;
                }
                if (isRecoverableTtsError(t)) {
                    log.warn("[Voice-V3] TTS skipped recoverable error conn={}, turn={}: {}", session.getConnectionId(), turnId, safeMsg(t));
                    latch.countDown();
                    return;
                }
                log.warn("[Voice-V3] TTS error conn={}, turn={}: {}", session.getConnectionId(), turnId, safeMsg(t));
                send(session, VoiceServerMessageV3.error(6003, "TTS 失败：" + safeMsg(t), true));
                latch.countDown();
            }
        });
        session.getCurrentTts().set(task);
        try {
            while (!latch.await(200, TimeUnit.MILLISECONDS)) {
                if (task.isDone()) break;
                if (!isCurrentPipelineActive(session, turnId)) break;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (isCurrentPipelineActive(session, turnId)) {
            log.info("[Voice-V3] TTS end conn={}, turn={}, bytes={}", session.getConnectionId(), turnId, audioBytes.get());
            send(session, VoiceServerMessageV3.ttsEnd(turnId));
        }
    }

    private String normalizeTtsText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("https?://\\S+", "")
                .replaceAll("(?i)(?<![A-Za-z0-9])C#(?![A-Za-z0-9])", "C sharp")
                .replaceAll("(?i)(?<![A-Za-z0-9])C\\+\\+(?![A-Za-z0-9])", "C plus plus")
                .replaceAll("(?<=[\\p{L}\\p{N}])_(?=[\\p{L}\\p{N}])", " 下划线 ")
                .replaceAll("(?m)^\\s*[-+•·]\\s+", "")
                .replaceAll("[*`~]+", "")
                .replaceAll("#{2,}", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isSpeakableText(String text) {
        return text != null && !text.isBlank() && SPEAKABLE_TEXT.matcher(text).find();
    }

    private boolean isRecoverableTtsError(Throwable t) {
        String msg = safeMsg(t).toLowerCase();
        return msg.contains("throttling.ratequota")
                || msg.contains("rate limit")
                || msg.contains("requests rate limit")
                || msg.contains("invalidparameter")
                || msg.contains("input text is valid");
    }

    private void resolveBusinessSession(VoiceSessionV3 session, VoiceClientMessageV3 msg) {
        boolean globalKb = Boolean.TRUE.equals(msg.getGlobalKb()) || session.isPublicJarvis();
        List<Long> effectiveKbIds = null;
        if (globalKb) {
            effectiveKbIds = listReadableKbIdsBySession(session);
        } else if (msg.getKbIds() != null) {
            for (Long kbId : msg.getKbIds()) {
                if (!isKbReadableBySession(session, kbId)) {
                    throw new IllegalArgumentException("无权访问知识库 kbId=" + kbId);
                }
            }
            effectiveKbIds = msg.getKbIds();
        }

        if (msg.getSessionId() != null) {
            AiSession existing = loadOwnedSession(session, msg.getSessionId());
            if (existing == null) throw new IllegalArgumentException("会话不存在或无权限");
            session.setAiSessionId(existing.getId());
            if (effectiveKbIds != null) {
                session.setKbIds(effectiveKbIds);
            } else if (existing.getKbIds() != null && !existing.getKbIds().isBlank()) {
                session.setKbIds(parseKbIds(existing.getKbIds()));
            }
            return;
        }

        if (session.getAiSessionId() != null) {
            if (effectiveKbIds != null) session.setKbIds(effectiveKbIds);
            return;
        }

        AiSession s = new AiSession();
        s.setTenantId(session.getTenantId());
        s.setUserId(session.getUserId());
        s.setTitle(session.isPublicJarvis() ? "Jarvis V3 免登录全局语音会话" : (globalKb ? "V3 全局语音会话" : "V3 语音会话"));
        s.setSessionType("VOICE");
        s.setStatus(1);
        s.setCreateTime(LocalDateTime.now());
        s.setUpdateTime(LocalDateTime.now());
        if (effectiveKbIds != null && !effectiveKbIds.isEmpty()) {
            s.setKbIds(effectiveKbIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        List<Long> finalEffectiveKbIds = effectiveKbIds == null ? List.of() : effectiveKbIds;
        withTenant(session, () -> aiSessionMapper.insert(s));
        session.setAiSessionId(s.getId());
        session.setKbIds(finalEffectiveKbIds);
    }

    private void gracefulEmptyTurn(VoiceSessionV3 session, String turnId, String reason) {
        cancelAsrOnly(session);
        setState(session, VoiceStateV3.IDLE);
        send(session, VoiceServerMessageV3.asrFinal(turnId, ""));
        send(session, VoiceServerMessageV3.turnEnd(turnId, reason));
    }

    private void handlePipelineError(VoiceSessionV3 session, String turnId, Exception e) {
        if (isPipelineInterrupted(session, turnId, e)) {
            finishInterruptedTurn(session, turnId);
            return;
        }
        log.warn("[Voice-V3] pipeline error conn={}, turn={}: {}", session.getConnectionId(), turnId, safeMsg(e), e);
        send(session, VoiceServerMessageV3.error(6001, "AI 服务异常：" + safeMsg(e), true));
        if (session.state() != VoiceStateV3.CLOSED) {
            setState(session, VoiceStateV3.IDLE);
            send(session, VoiceServerMessageV3.turnEnd(turnId, "PIPELINE_ERROR"));
        }
    }

    private void cancelInflight(VoiceSessionV3 session) {
        cancelAsrOnly(session);
        TtsTask t = session.getCurrentTts().getAndSet(null);
        if (t != null) try { t.cancel(); } catch (Exception ignore) {}
        var f = session.getCurrentLlm().getAndSet(null);
        if (f != null) f.cancel(true);
        // TTS 队列消费 worker 也必须 cancel，否则它会继续从 BlockingQueue 拿剩下的
        // 句子合成发音，造成"挂断后 AI 还在说"。cancel(true) 会 interrupt worker，
        // 让其在 BlockingQueue.poll(timeout) 处抛 InterruptedException 立即退出。
        var tw = session.getCurrentTtsWorker().getAndSet(null);
        if (tw != null) tw.cancel(true);
    }

    private void cancelAsrOnly(VoiceSessionV3 session) {
        AsrSession a = session.getAsrSession();
        session.setAsrSession(null);
        if (a != null) try { a.cancel(); } catch (Exception ignore) {}
    }

    private boolean isCurrentPipelineActive(VoiceSessionV3 session, String turnId) {
        return session != null
                && turnId != null
                && turnId.equals(session.getCurrentTurnId())
                && session.state() != VoiceStateV3.IDLE
                && session.state() != VoiceStateV3.CLOSED;
    }

    private boolean isPipelineInterrupted(VoiceSessionV3 session, String turnId, Throwable t) {
        return Thread.currentThread().isInterrupted()
                || !isCurrentPipelineActive(session, turnId)
                || hasInterruptedCause(t)
                || hasCancellationCause(t);
    }

    private void finishInterruptedTurn(VoiceSessionV3 session, String turnId) {
        log.info("[Voice-V3] pipeline interrupted conn={}, turn={}, currentTurn={}, state={}",
                session.getConnectionId(), turnId, session.getCurrentTurnId(), session.state());
        if (turnId != null
                && turnId.equals(session.getCurrentTurnId())
                && session.state() != VoiceStateV3.IDLE
                && session.state() != VoiceStateV3.CLOSED) {
            setState(session, VoiceStateV3.IDLE);
            send(session, VoiceServerMessageV3.turnEnd(turnId, "INTERRUPTED"));
        }
    }

    private boolean hasInterruptedCause(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof InterruptedException) return true;
        }
        return false;
    }

    private boolean hasCancellationCause(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof CancellationException) return true;
            if ("__interrupted__".equals(cur.getMessage())) return true;
        }
        return false;
    }

    private void setState(VoiceSessionV3 session, VoiceStateV3 state) {
        session.forceState(state);
        send(session, VoiceServerMessageV3.state(state));
    }

    public void send(VoiceSessionV3 session, VoiceServerMessageV3 msg) {
        if (session == null) return;
        send(session.getWs(), msg);
    }

    public void send(WebSocketSession ws, VoiceServerMessageV3 msg) {
        if (ws == null || !ws.isOpen() || msg == null) return;
        try {
            String json = om.writeValueAsString(msg);
            synchronized (ws) { ws.sendMessage(new TextMessage(json)); }
        } catch (Exception e) {
            log.debug("[Voice-V3] send text failed: {}", e.getMessage());
        }
    }

    private void sendBinary(WebSocketSession ws, byte[] data) {
        if (ws == null || !ws.isOpen() || data == null || data.length == 0) return;
        try {
            synchronized (ws) { ws.sendMessage(new BinaryMessage(ByteBuffer.wrap(data))); }
        } catch (Exception e) {
            log.debug("[Voice-V3] send binary failed: {}", e.getMessage());
        }
    }

    private VoiceModeV3 parseMode(String mode) {
        if (mode == null || mode.isBlank()) return VoiceModeV3.HANDS_FREE;
        try { return VoiceModeV3.valueOf(mode.trim().toUpperCase()); }
        catch (Exception ignored) { return VoiceModeV3.HANDS_FREE; }
    }

    private int peak16(byte[] audio) {
        if (audio == null) return 0;
        int peak = 0;
        for (int i = 0; i + 1 < audio.length; i += 2) {
            int sample = (short) ((audio[i] & 0xff) | (audio[i + 1] << 8));
            int abs = Math.abs(sample);
            if (abs > peak) peak = abs;
        }
        return peak;
    }

    private int rms16(byte[] audio) {
        if (audio == null || audio.length < 2) return 0;
        long sumSq = 0;
        int samples = 0;
        for (int i = 0; i + 1 < audio.length; i += 2) {
            int sample = (short) ((audio[i] & 0xff) | (audio[i + 1] << 8));
            sumSq += (long) sample * sample;
            samples++;
        }
        if (samples == 0) return 0;
        return (int) Math.sqrt((double) sumSq / samples);
    }

    private boolean isNoValidAudio(Throwable t) {
        String msg = safeMsg(t).toUpperCase();
        return msg.contains("NO_VALID_AUDIO") || msg.contains("NO VALID AUDIO");
    }

    private List<Long> listReadableKbIdsBySession(VoiceSessionV3 session) {
        try {
            List<KnowledgeBase> rows = withTenantCall(session, () -> kbMapper.selectList(
                    Wrappers.<KnowledgeBase>lambdaQuery()
                            .eq(KnowledgeBase::getStatus, 1)
                            .orderByDesc(KnowledgeBase::getUpdateTime)));
            if (rows == null || rows.isEmpty()) return List.of();
            List<Long> ids = new ArrayList<>();
            for (KnowledgeBase kb : rows) {
                if (kb == null || kb.getId() == null) continue;
                if (kb.getTenantId() != null && !kb.getTenantId().equals(session.getTenantId())) continue;
                // 白名单式可见性判定：只把 PUBLIC 当作完全公开；DEPT / ROLE / USER 等"半公开"在 ACL
                // 表实现之前按 PRIVATE 处理，避免免登录 Jarvis 入口越权读到部门/角色限定的知识库。
                boolean isPublic = "PUBLIC".equalsIgnoreCase(kb.getVisibility());
                if (session.isPublicJarvis()) {
                    if (isPublic) ids.add(kb.getId());
                } else if (isPublic || (kb.getOwnerId() != null && kb.getOwnerId().equals(session.getUserId()))) {
                    ids.add(kb.getId());
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("[Voice-V3] list readable kb failed userId={}: {}", session.getUserId(), e.getMessage());
            return List.of();
        }
    }

    private boolean isKbReadableBySession(VoiceSessionV3 session, Long kbId) {
        if (kbId == null) return false;
        try {
            KnowledgeBase kb = withTenantCall(session, () -> kbMapper.selectById(kbId));
            if (kb == null) return false;
            if (kb.getStatus() == null || kb.getStatus() != 1) return false;
            if (kb.getTenantId() != null && !kb.getTenantId().equals(session.getTenantId())) return false;
            // 白名单：仅 visibility=PUBLIC 才视为公开；与 listReadableKbIdsBySession 保持一致。
            boolean isPublic = "PUBLIC".equalsIgnoreCase(kb.getVisibility());
            if (session.isPublicJarvis()) return isPublic;
            return isPublic || (kb.getOwnerId() != null && kb.getOwnerId().equals(session.getUserId()));
        } catch (Exception e) {
            log.warn("[Voice-V3] kb readable check failed kbId={}: {}", kbId, e.getMessage());
            return false;
        }
    }

    private AiSession loadOwnedSession(VoiceSessionV3 session, Long sessionId) {
        return withTenantCall(session, () -> {
            AiSession s = aiSessionMapper.selectById(sessionId);
            if (s == null) return null;
            if (!s.getUserId().equals(session.getUserId())) return null;
            return s;
        });
    }

    private AiMessage newMsg(VoiceSessionV3 session, String role, String content) {
        AiMessage m = new AiMessage();
        m.setTenantId(session.getTenantId());
        m.setSessionId(session.getAiSessionId());
        m.setUserId(session.getUserId());
        m.setRole(role);
        m.setContent(content);
        m.setFeedback(0);
        m.setCreateTime(LocalDateTime.now());
        return m;
    }

    /**
     * 语音风格附加指令：语音会被 TTS 直接朗读，必须开门见山。
     * 禁止"好的/没问题/接下来给你一份…"等前置铺垫和元描述（例如"以下是一份演讲稿"）。
     */
    private static final String VOICE_STYLE_SYSTEM_PROMPT = """
            语音输出风格（强制遵守）：
            1. 直接开始正文，不要任何前置铺垫，例如：不要说"好的"、"没问题"、"当然可以"、"接下来我给你一份"、"以下是…"、"下面是…"、"这是一份…"。
            2. 不要在开头介绍"我将/我会/我来给你讲一下"等元描述，也不要在结尾加"以上就是…"、"希望对你有帮助"等收尾套话。
            3. 第一句就是用户想听的内容本身；语气自然口语，避免书面排版和列表标号被朗读出来。
            4. 如果用户的请求需要较长内容（如稿件、介绍、说明），直接输出内容正文，不要先复述用户需求。
            5. 垂类方案类问题不能因为是语音就只讲结论；至少讲清业务判断、关键模块、数据或接口流转、实施步骤、风险边界和验收口径。
            6. 语音回答可以少用编号，但每个要点必须有具体业务含义和实现要点，避免只说“提升效率、加强管理、优化流程”等空话。
            """;

    private List<ChatMessage> buildPrompt(VoiceSessionV3 session, String userText, List<RetrievedChunk> chunks, Long currentUserMsgId) {
        List<ChatMessage> ragPrompt = ragService.buildPrompt(userText, chunks);
        List<ChatMessage> prompt = new ArrayList<>();
        String personaPrompt = personaService.systemPrompt(session.getPersonaId());
        if (!ragPrompt.isEmpty()) {
            ChatMessage ragSystem = ragPrompt.get(0);
            String systemPrompt = ragSystem.getContent();
            if (personaPrompt != null && !personaPrompt.isBlank()) {
                systemPrompt = systemPrompt + "\n\n" + personaPrompt + """

                        人设优先级补充：
                        1. 如果用户询问“你是什么角色/你的身份/你负责什么”，优先回答当前选择的人设名称、职责和表达风格。
                        2. 回答企业知识问题时，仍然必须遵守上方知识库、事实边界和安全约束。
                        """;
            }
            prompt.add(ChatMessage.system(systemPrompt));
        }
        // 语音风格约束：放在 RAG/通用 system 之后，紧贴历史与本轮用户输入，权重更高
        prompt.add(ChatMessage.system(VOICE_STYLE_SYSTEM_PROMPT));
        List<AiMessage> history = withTenantCall(session, () -> aiMessageMapper.selectList(
                Wrappers.<AiMessage>lambdaQuery()
                        .eq(AiMessage::getSessionId, session.getAiSessionId())
                        .ne(currentUserMsgId != null, AiMessage::getId, currentUserMsgId)
                        .in(AiMessage::getRole, List.of("user", "assistant"))
                        .orderByDesc(AiMessage::getCreateTime)
                        .last("LIMIT 12")));
        Collections.reverse(history);
        for (AiMessage m : history) {
            if (m.getContent() != null && !m.getContent().isBlank()) {
                prompt.add(new ChatMessage(m.getRole(), m.getContent()));
            }
        }
        prompt.add(ChatMessage.user(userText));
        return prompt;
    }

    private void updateVoiceSession(VoiceSessionV3 session, String userText) {
        withTenant(session, () -> {
            AiSession current = aiSessionMapper.selectById(session.getAiSessionId());
            if (current == null) return;
            AiSession upd = new AiSession();
            upd.setId(session.getAiSessionId());
            upd.setUpdateTime(LocalDateTime.now());
            String text = userText == null ? "" : userText.trim();
            if (!text.isBlank() && (current.getTitle() == null || current.getTitle().isBlank() || current.getTitle().contains("语音会话"))) {
                upd.setTitle(text.length() > 32 ? text.substring(0, 32) : text);
            }
            if (session.getKbIds() != null && !session.getKbIds().isEmpty()) {
                upd.setKbIds(session.getKbIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
            aiSessionMapper.updateById(upd);
        });
    }

    private void persistReferences(VoiceSessionV3 session, Long assistantMsgId, List<RetrievedChunk> chunks) {
        for (RetrievedChunk c : chunks) {
            AiMessageReference ref = new AiMessageReference();
            ref.setTenantId(session.getTenantId());
            ref.setMessageId(assistantMsgId);
            ref.setDocumentId(c.getDocumentId());
            ref.setChunkId(c.getChunkId());
            ref.setScore(BigDecimal.valueOf(c.getScore()).setScale(6, RoundingMode.HALF_UP));
            String snap = c.getContent();
            if (snap != null) ref.setContentSnapshot(snap.length() > 1000 ? snap.substring(0, 1000) : snap);
            ref.setCreateTime(LocalDateTime.now());
            withTenant(session, () -> referenceMapper.insert(ref));
        }
    }

    private List<Long> parseKbIds(String csv) {
        return Arrays.stream(csv.split(",")).filter(s -> !s.isBlank())
                .map(String::trim).map(Long::parseLong).toList();
    }

    private List<Map<String, Object>> toRefView(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("chunkId", c.getChunkId());
            v.put("documentId", c.getDocumentId());
            v.put("kbId", c.getKbId());
            v.put("title", c.getTitle());
            v.put("score", c.getScore());
            String snap = c.getContent();
            v.put("snippet", snap == null ? "" : (snap.length() > 240 ? snap.substring(0, 240) + "…" : snap));
            return v;
        }).collect(Collectors.toList());
    }

    private void withTenant(VoiceSessionV3 session, Runnable r) {
        try (TenantContext.Scope tenantScope = TenantContext.withTenant(session.getTenantId())) {
            r.run();
        }
    }

    private <T> T withTenantCall(VoiceSessionV3 session, java.util.function.Supplier<T> s) {
        try (TenantContext.Scope tenantScope = TenantContext.withTenant(session.getTenantId())) {
            return s.get();
        }
    }

    private static String safeMsg(Throwable t) {
        if (t == null) return "Unknown error";
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
