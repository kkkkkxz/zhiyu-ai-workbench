package com.zwskchat.zwskchat.chat.service.impl;

import com.zwskchat.zwskchat.ai.AiChatClient;
import com.zwskchat.zwskchat.ai.dto.ChatMessage;
import com.zwskchat.zwskchat.ai.hub.IntentRouter;
import com.zwskchat.zwskchat.ai.hub.IntentType;
import com.zwskchat.zwskchat.ai.hub.PresalesIntentClassifier;
import com.zwskchat.zwskchat.ai.hub.PresalesTaskDomain;
import com.zwskchat.zwskchat.ai.hub.TenantSafeSchemaProvider;
import com.zwskchat.zwskchat.ai.hub.TextToSqlEngine;
import com.zwskchat.zwskchat.chat.dto.CompletionRequest;
import com.zwskchat.zwskchat.chat.entity.AiMessage;
import com.zwskchat.zwskchat.chat.entity.AiMessageReference;
import com.zwskchat.zwskchat.chat.entity.AiSession;
import com.zwskchat.zwskchat.chat.mapper.AiMessageMapper;
import com.zwskchat.zwskchat.chat.mapper.AiMessageReferenceMapper;
import com.zwskchat.zwskchat.chat.mapper.AiSessionMapper;
import com.zwskchat.zwskchat.chat.service.ChatService;
import com.zwskchat.zwskchat.chat.service.SessionService;
import com.zwskchat.zwskchat.kb.service.KnowledgeBaseService;
import com.zwskchat.zwskchat.common.api.Result;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.persona.service.PersonaService;
import com.zwskchat.zwskchat.rag.RagService;
import com.zwskchat.zwskchat.rag.confidence.ConfidenceResult;
import com.zwskchat.zwskchat.rag.confidence.ConfidenceScoringService;
import com.zwskchat.zwskchat.rag.confidence.GovernanceResult;
import com.zwskchat.zwskchat.rag.confidence.HighRiskSlotDetector;
import com.zwskchat.zwskchat.rag.confidence.ResponseGovernanceService;
import com.zwskchat.zwskchat.metrics.RagMetricsRecorder;
import com.zwskchat.zwskchat.rag.dto.RetrievedChunk;
import com.zwskchat.zwskchat.safety.ContentSafetyService;
import com.zwskchat.zwskchat.security.LoginUser;
import com.zwskchat.zwskchat.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zwskchat.zwskchat.stock.dto.StockDialogueAnswer;
import com.zwskchat.zwskchat.stock.service.StockDialogueService;
import com.zwskchat.zwskchat.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final SessionService sessionService;
    private final KnowledgeBaseService kbService;
    private final RagService ragService;
    private final ConfidenceScoringService confidenceScoring;
    private final ResponseGovernanceService responseGovernance;
    private final RagMetricsRecorder ragMetrics;
    private final AiChatClient chatClient;
    private final AiSessionMapper sessionMapper;
    private final AiMessageMapper messageMapper;
    private final AiMessageReferenceMapper referenceMapper;
    private final ContentSafetyService contentSafety;
    private final StockDialogueService stockDialogueService;
    private final PersonaService personaService;
    private final IntentRouter intentRouter;
    private final TextToSqlEngine textToSqlEngine;
    private final TenantSafeSchemaProvider schemaProvider;
    private final AiProperties aiProps;

    @Qualifier("commonExecutor")
    private final ThreadPoolTaskExecutor executor;

    @Override
    public SseEmitter completionsStream(Long sessionId, CompletionRequest req) {
        AiSession session = sessionService.getOwn(sessionId);
        LoginUser user = SecurityUtils.currentUser();
        Long tenantId = user.getTenantId();
        Long userId = user.getUserId();

        // 解析 kbIds：优先入参，其次 session 上的关联
        List<Long> kbIds = req.getKbIds();
        if ((kbIds == null || kbIds.isEmpty()) && session.getKbIds() != null && !session.getKbIds().isBlank()) {
            kbIds = Arrays.stream(session.getKbIds().split(","))
                    .filter(s -> !s.isBlank())
                    .map(String::trim).map(Long::parseLong).toList();
        }
        if (kbIds == null) kbIds = List.of();

        // 校验 kbIds 归属：防止传入别人的私有知识库 ID
        for (Long k : kbIds) {
            kbService.assertReadable(k);
        }

        // SseEmitter 设置较长超时（与 chat-timeout 对齐 + 余量）。
        // 同时把异步 future 暴露给 emitter 的 timeout / error 钩子，确保连接断开时取消后台 LLM 调用，
        // 避免"前端早走了，后端还在烧 token / 占连接"的资源浪费。
        SseEmitter emitter = new SseEmitter(180_000L);
        AtomicReference<Future<?>> taskRef = new AtomicReference<>();
        Runnable cancelTask = () -> {
            Future<?> f = taskRef.getAndSet(null);
            if (f != null) f.cancel(true);
        };
        emitter.onTimeout(() -> {
            log.warn("[SSE] timeout session={}, cancelling background task", sessionId);
            cancelTask.run();
        });
        emitter.onError(t -> {
            if (t instanceof java.io.IOException && t.getMessage() != null && t.getMessage().contains("已建立的连接")) {
                log.info("[SSE] client disconnected session={}", sessionId);
            } else {
                log.warn("[SSE] error session={} : {}", sessionId, t.getMessage());
            }
            cancelTask.run();
        });
        emitter.onCompletion(cancelTask::run);

        final List<Long> finalKbIds = kbIds;
        final long t0 = System.currentTimeMillis();

        Future<?> task = executor.submit(() -> {
            // 子线程恢复租户上下文（主线程 ThreadLocal 不会传播）
            try (TenantContext.Scope tenantScope = TenantContext.withTenant(tenantId)) {
                // 1. 落库用户消息
                AiMessage userMsg = buildMessage(tenantId, sessionId, userId, "user", req.getQuestion());
                messageMapper.insert(userMsg);

                Optional<StockDialogueAnswer> stockAnswer = stockDialogueService.tryAnswer(req.getQuestion(), false);
                if (stockAnswer.isPresent()) {
                    long cost = System.currentTimeMillis() - t0;
                    String answer = stockAnswer.get().text();
                    ContentSafetyService.SafetyResult safety = contentSafety.check(answer);
                    String safeAnswer = safety.safe() ? answer : safety.sanitized();

                    try {
                        emitter.send(SseEmitter.event().name("references").data(List.of()));
                        emitter.send(SseEmitter.event().name("delta").data(safeAnswer));
                    } catch (java.io.IOException e) {
                        log.info("[SSE] client disconnected during stock answer, session={}", sessionId);
                        return;
                    }

                    AiMessage assistant = buildMessage(tenantId, sessionId, userId, "assistant", safeAnswer);
                    assistant.setLatencyMs((int) Math.min(cost, Integer.MAX_VALUE));
                    messageMapper.insert(assistant);

                    Map<String, Object> done = new HashMap<>();
                    done.put("messageId", assistant.getId());
                    done.put("latencyMs", cost);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(done));
                        emitter.complete();
                    } catch (java.io.IOException e) {
                        log.info("[SSE] client disconnected before stock completion, session={}", sessionId);
                    }
                    return;
                }

                // 2. Intent routing — DATA_QUERY 走 Text-to-SQL，其余走 RAG
                IntentType intent = IntentType.GENERAL;
                try {
                    intent = intentRouter.classify(req.getQuestion());
                } catch (Exception e) {
                    log.warn("[Chat] intent classify failed, fallback to GENERAL", e);
                }

                if (intent == IntentType.DATA_QUERY) {
                    long costSql = System.currentTimeMillis() - t0;
                    try {
                        String schema = schemaProvider.getSchemaForCurrentTenant();
                        TextToSqlEngine.SqlQueryResult sqlResult = textToSqlEngine.execute(
                                req.getQuestion(),
                                schema,
                                sql -> schemaProvider.isTenantSafe(sql, tenantId),
                                "查询未通过租户隔离校验，请重新提问");

                        if (sqlResult.success) {
                            Map<String, Object> tableEvent = new LinkedHashMap<>();
                            tableEvent.put("columns", sqlResult.columns);
                            tableEvent.put("rows", sqlResult.rows);
                            tableEvent.put("sql", sqlResult.sql);
                            tableEvent.put("executionMs", sqlResult.executionMs);

                            String summary = "查询完成，共 " + sqlResult.rows.size() + " 条结果。";
                            emitter.send(SseEmitter.event().name("delta").data(summary));
                            emitter.send(SseEmitter.event().name("data_table").data(tableEvent));

                            costSql = System.currentTimeMillis() - t0;
                            AiMessage assistant = buildMessage(tenantId, sessionId, userId, "assistant",
                                    summary + "\n```sql\n" + sqlResult.sql + "\n```");
                            assistant.setLatencyMs((int) Math.min(costSql, Integer.MAX_VALUE));
                            messageMapper.insert(assistant);

                            Map<String, Object> done = new HashMap<>();
                            done.put("messageId", assistant.getId());
                            done.put("latencyMs", costSql);
                            emitter.send(SseEmitter.event().name("done").data(done));
                            emitter.complete();
                        } else {
                            String errMsg = sqlResult.error != null ? sqlResult.error : "数据查询失败";
                            emitter.send(SseEmitter.event().name("delta").data(errMsg));

                            AiMessage assistant = buildMessage(tenantId, sessionId, userId, "assistant", errMsg);
                            assistant.setLatencyMs((int) Math.min(costSql, Integer.MAX_VALUE));
                            messageMapper.insert(assistant);

                            Map<String, Object> done = new HashMap<>();
                            done.put("messageId", assistant.getId());
                            done.put("latencyMs", costSql);
                            emitter.send(SseEmitter.event().name("done").data(done));
                            emitter.complete();
                        }
                    } catch (Exception e) {
                        log.error("[Chat] text-to-sql failed", e);
                        sendError(emitter, ResultCode.INTERNAL_ERROR.getCode(), "数据查询异常");
                    }
                    return;
                }

                // 3. 检索（RAG 路径）
                long retrieveStart = System.currentTimeMillis();
                List<RetrievedChunk> chunks = ragService.retrieve(finalKbIds, req.getQuestion());
                ragMetrics.recordRetrieval(chunks == null ? 0 : chunks.size(),
                        System.currentTimeMillis() - retrieveStart);

                // 3a. 可信度组合评分（对应工程计划书“可信度评分”章节）：
                //     此处仅计算并上报，便于前端展示置信度、运维回溯；不强制改变回答行为，
                //     低置信硬拒答可在后续按业务开关启用。
                ConfidenceResult confidence = confidenceScoring.score(req.getQuestion(), chunks);
                log.info("[Chat] confidence session={}, score={}, decision={}, components={}",
                        sessionId, String.format(java.util.Locale.ROOT, "%.4f", confidence.confidence()),
                        confidence.decision(), confidence.components());

                // 3b. 回答治理：根据置信度决策生成 CTA / 谨慎提示 / 拒答引导
                //     （对应工程计划书“低置信拒答、输出带引导动作”原则）。
                // 叠加高风险槽位强制人工复核（报价/政策/实验参数/资质承诺/交付工期）。
                GovernanceResult governance = responseGovernance.govern(confidence, req.getQuestion());
                List<String> highRiskSlots = HighRiskSlotDetector.detect(req.getQuestion());
                ragMetrics.recordConfidence(confidence.decision(), confidence.confidence());

                // 售前四大任务域识别（对应工程计划书“客户需求匹配/案例检索/资料查询/方案草拟”），
                // 仅用于上报与回答结构编排，错分不影响事实正确性。
                PresalesTaskDomain taskDomain = aiProps.getPresales() != null && aiProps.getPresales().isEnabled()
                        ? PresalesIntentClassifier.classify(req.getQuestion())
                        : PresalesTaskDomain.GENERAL;

                // 推送 references / task_domain / confidence / governance 事件，前端可立刻渲染引用面板、任务域、置信度与引导动作
                try {
                    emitter.send(SseEmitter.event().name("references").data(toRefView(chunks)));
                    emitter.send(SseEmitter.event().name("task_domain").data(taskDomain.name()));
                    emitter.send(SseEmitter.event().name("confidence").data(toConfidenceView(confidence)));
                    emitter.send(SseEmitter.event().name("governance").data(toGovernanceView(governance, highRiskSlots)));
                } catch (java.io.IOException e) {
                    log.info("[SSE] client disconnected before references sent, session={}", sessionId);
                    return;
                }

                // 3c. 硬拒答短路（仅当 zwsk.ai.governance.enforce-refusal=true 且置信度判为 REFUSE）：
                //     直接返回补充信息 + 转人工/预约引导，不调用 LLM；默认关闭，不影响既有行为。
                if (governance.isHardRefusal()) {
                    String refusalText = responseGovernance.renderRefusalText(governance);
                    try {
                        emitter.send(SseEmitter.event().name("delta").data(refusalText));
                    } catch (java.io.IOException e) {
                        log.info("[SSE] client disconnected before refusal sent, session={}", sessionId);
                        return;
                    }
                    long refusalCost = System.currentTimeMillis() - t0;
                    ragMetrics.recordRefusal();
                    ragMetrics.recordAnswerLatency(refusalCost);
                    AiMessage refusalMsg = buildMessage(tenantId, sessionId, userId, "assistant", refusalText);
                    refusalMsg.setLatencyMs((int) Math.min(refusalCost, Integer.MAX_VALUE));
                    messageMapper.insert(refusalMsg);
                    Map<String, Object> done = new HashMap<>();
                    done.put("messageId", refusalMsg.getId());
                    done.put("latencyMs", refusalCost);
                    done.put("refused", true);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(done));
                    } catch (java.io.IOException ignore) {
                        // 客户端已断开，直接收尾
                    }
                    emitter.complete();
                    return;
                }

                // 3. 流式 LLM（拼上会话历史实现多轮上下文）
                List<ChatMessage> prompt = buildPromptWithHistory(
                        sessionId, userMsg.getId(), req.getQuestion(), chunks, req.getPersonaId());
                AtomicReference<AiChatClient.UsageInfo> usageRef = new AtomicReference<>();
                String full;
                try {
                    full = chatClient.streamChat(prompt,
                            delta -> {
                                try {
                                    emitter.send(SseEmitter.event().name("delta").data(delta));
                                } catch (java.io.IOException e) {
                                    log.debug("[SSE] client disconnected during streaming, session={}", sessionId);
                                    throw new RuntimeException("Client disconnected", e);
                                }
                            },
                            usageRef::set);
                } catch (BusinessException be) {
                    sendError(emitter, be.getCode(), be.getMessage());
                    return;
                } catch (RuntimeException re) {
                    if (re.getCause() instanceof java.io.IOException) {
                        log.info("[SSE] client disconnected during LLM streaming, session={}", sessionId);
                        return;
                    }
                    // emitter onCompletion/onError/onTimeout 触发 cancelTask 后，子线程被 interrupt，
                    // Provider（DashScope/...）会把这种"上游被取消"统一抛成 CancellationException("__interrupted__")。
                    // 这是用户主动取消、刷新、切页面等场景下的预期行为，不应记 ERROR、不应给前端 sendError
                    // （emitter 已 complete/abort，再 send 也只是再触发一次 IO 异常）。
                    if (isClientCancellation(re)) {
                        log.info("[SSE] streaming cancelled (client/task aborted), session={}", sessionId);
                        return;
                    }
                    throw re;
                } catch (Exception e) {
                    log.error("[Chat] llm error", e);
                    sendError(emitter, ResultCode.AI_SERVICE_UNAVAILABLE.getCode(), "AI 服务暂时不可用");
                    return;
                }

                long cost = System.currentTimeMillis() - t0;
                ragMetrics.recordAnswerLatency(cost);

                // 4a. 内容安全兜底：LLM 全部生成完后再审一次，命中则：
                //   - 发 moderation 事件让前端把已累加的 delta 替换为安全文案；
                //   - assistant 消息用 sanitized 文本落库，保证历史记录永远是干净的。
                ContentSafetyService.SafetyResult safety = contentSafety.check(full);
                String safeFull = safety.safe() ? full : safety.sanitized();
                if (!safety.safe()) {
                    Map<String, Object> modEvent = new HashMap<>();
                    modEvent.put("sanitized", safeFull);
                    try {
                        emitter.send(SseEmitter.event().name("moderation").data(modEvent));
                    } catch (java.io.IOException e) {
                        log.debug("[SSE] client disconnected before moderation event, session={}", sessionId);
                    }
                }

                // 4b. 落库 assistant 消息 + 引用
                AiMessage assistant = buildMessage(tenantId, sessionId, userId, "assistant", safeFull);
                assistant.setLatencyMs((int) Math.min(cost, Integer.MAX_VALUE));
                AiChatClient.UsageInfo usage = usageRef.get();
                if (usage != null) {
                    assistant.setTokenIn(usage.promptTokens());
                    assistant.setTokenOut(usage.completionTokens());
                }
                // 回答可追溯字段（对应工程计划书“回溯机制：保存 model_id、prompt_hash、policy_version、confidence”）。
                assistant.setModelId(chatClient.activeModel());
                assistant.setPromptHash(promptHash(prompt));
                assistant.setPolicyVersion(aiProps.getGovernance().getPolicyVersion());
                assistant.setConfidenceScore(BigDecimal.valueOf(confidence.confidence()).setScale(4, RoundingMode.HALF_UP));
                assistant.setConfidenceDecision(confidence.decision().name());
                messageMapper.insert(assistant);

                // 引用快照
                for (RetrievedChunk c : chunks) {
                    AiMessageReference ref = new AiMessageReference();
                    ref.setTenantId(tenantId);
                    ref.setMessageId(assistant.getId());
                    ref.setDocumentId(c.getDocumentId());
                    ref.setChunkId(c.getChunkId());
                    ref.setScore(BigDecimal.valueOf(c.getScore()).setScale(6, RoundingMode.HALF_UP));
                    String snap = c.getContent();
                    if (snap != null) {
                        ref.setContentSnapshot(snap.length() > 1000 ? snap.substring(0, 1000) : snap);
                    }
                    ref.setCreateTime(LocalDateTime.now());
                    referenceMapper.insert(ref);
                }

                // 5. 更新会话标题（用首条问题前 32 字）
                if ("新会话".equals(session.getTitle())) {
                    AiSession upd = new AiSession();
                    upd.setId(session.getId());
                    String t = req.getQuestion().trim();
                    upd.setTitle(t.length() > 32 ? t.substring(0, 32) : t);
                    upd.setUpdateTime(LocalDateTime.now());
                    sessionMapper.updateById(upd);
                }

                // 6. 完成
                Map<String, Object> done = new HashMap<>();
                done.put("messageId", assistant.getId());
                done.put("latencyMs", cost);
                if (usage != null) {
                    done.put("tokenIn", usage.promptTokens());
                    done.put("tokenOut", usage.completionTokens());
                }
                try {
                    emitter.send(SseEmitter.event().name("done").data(done));
                    emitter.complete();
                } catch (java.io.IOException e) {
                    log.info("[SSE] client disconnected before completion, session={}", sessionId);
                }

            } catch (Exception e) {
                // 同样兜底：异步线程整体被 interrupt / 取消时，不要打 ERROR 堆栈
                if (isClientCancellation(e)) {
                    log.info("[SSE] task cancelled before completion, session={}", sessionId);
                } else {
                    log.error("[Chat] stream failed", e);
                    sendError(emitter, ResultCode.INTERNAL_ERROR.getCode(), "服务异常");
                }
            }
        });
        taskRef.set(task);

        return emitter;
    }

    /**
     * 是否属于"客户端断开 / 任务被外部 cancel"导致的中断。
     *
     * <p>来源场景：
     * <ul>
     *   <li>SseEmitter onCompletion / onError / onTimeout 触发 future.cancel(true)，runner 线程被 interrupt</li>
     *   <li>Provider（DashScope/...）将上游中断统一包装成 CancellationException("__interrupted__")</li>
     *   <li>线程已被打上 interrupt flag（即使异常类型不直观）</li>
     * </ul>
     * 这些都是预期行为，应降级为 INFO 日志，且不要再向已断开的 emitter 写错误事件。</p>
     */
    private static boolean isClientCancellation(Throwable t) {
        if (Thread.currentThread().isInterrupted()) return true;
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof InterruptedException) return true;
            if (cur instanceof java.util.concurrent.CancellationException) return true;
            if ("__interrupted__".equals(cur.getMessage())) return true;
        }
        return false;
    }

    /**
     * 拼装带历史上下文的 prompt：
     * [RAG system] + [最近 12 条 user/assistant 历史（不含当前提问）] + [当前用户提问]
     */
    private List<ChatMessage> buildPromptWithHistory(Long sessionId, Long currentUserMsgId,
                                                     String question, List<RetrievedChunk> chunks,
                                                     String personaId) {
        List<ChatMessage> ragPrompt = ragService.buildPrompt(question, chunks);
        List<ChatMessage> prompt = new ArrayList<>();
        String personaPrompt = personaService.systemPrompt(personaId);
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

        List<AiMessage> history = messageMapper.selectList(
                Wrappers.<AiMessage>lambdaQuery()
                        .eq(AiMessage::getSessionId, sessionId)
                        .ne(currentUserMsgId != null, AiMessage::getId, currentUserMsgId)
                        .in(AiMessage::getRole, List.of("user", "assistant"))
                        .orderByDesc(AiMessage::getCreateTime)
                        .last("LIMIT 12"));
        Collections.reverse(history);
        for (AiMessage m : history) {
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            prompt.add(new ChatMessage(m.getRole(), m.getContent()));
        }
        prompt.add(ChatMessage.user(question));
        return prompt;
    }

    private AiMessage buildMessage(Long tenantId, Long sessionId, Long userId, String role, String content) {
        AiMessage m = new AiMessage();
        m.setTenantId(tenantId);
        m.setSessionId(sessionId);
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setFeedback(0);
        m.setCreateTime(LocalDateTime.now());
        return m;
    }

    private List<Map<String, Object>> toRefView(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("chunkId", c.getChunkId());
            v.put("documentId", c.getDocumentId());
            v.put("kbId", c.getKbId());
            v.put("title", c.getTitle());
            v.put("score", c.getScore());
            if (c.getPageNumber() != null) {
                v.put("pageNumber", c.getPageNumber());
            }
            String snap = c.getContent();
            if (snap == null) {
                v.put("snippet", "");
            } else {
                v.put("snippet", snap.length() > 240 ? snap.substring(0, 240) + "…" : snap);
            }
            return v;
        }).collect(Collectors.toList());
    }

    /** 置信度上报视图：总分 + 三档决策 + 各分量明细（便于前端展示与回溯）。 */
    private Map<String, Object> toConfidenceView(ConfidenceResult confidence) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("score", BigDecimal.valueOf(confidence.confidence()).setScale(4, RoundingMode.HALF_UP));
        v.put("decision", confidence.decision().name());
        Map<String, Object> components = new LinkedHashMap<>();
        confidence.components().forEach((k, val) ->
                components.put(k, BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP)));
        v.put("components", components);
        return v;
    }

    /** 治理上报视图：动作 + 谨慎提示 + 需补充信息 + CTA 引导动作 + 命中的高风险槽位。 */
    private Map<String, Object> toGovernanceView(GovernanceResult governance, List<String> highRiskSlots) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("action", governance.action().name());
        v.put("note", governance.note());
        v.put("requiredInfo", governance.requiredInfo());
        v.put("cta", governance.ctaActions());
        v.put("highRiskSlots", highRiskSlots == null ? List.of() : highRiskSlots);
        v.put("humanReviewRequired", highRiskSlots != null && !highRiskSlots.isEmpty());
        return v;
    }

    private void sendError(SseEmitter emitter, int code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Result.fail(code, message)));
        } catch (Exception ignore) {}
        emitter.complete();
    }

    /**
     * 计算提示词指纹（SHA-256，仅取 system 消息内容，即固化的业务边界 + 检索证据上下文）。
     * 用于回答回溯与提示词回归：同一指纹意味着同一套 system prompt + 同一批证据。
     */
    private static String promptHash(List<ChatMessage> prompt) {
        if (prompt == null || prompt.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : prompt) {
            if (m != null && "system".equals(m.getRole()) && m.getContent() != null) {
                sb.append(m.getContent());
            }
        }
        if (sb.length() == 0) return null;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return null;
        }
    }
}
