import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowRight, BadgeCheck, Sparkles, UserRound } from 'lucide-react';
import { toast } from 'sonner';

import { cn } from '@/lib/utils';
import {
  getPersonaProfile,
  getSessionPersonaId,
  storeSessionPersonaId,
  type AssistantPersona,
} from '@/lib/personas';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Skeleton } from '@/components/ui/skeleton';
import { MessageItem, type ChatTurn } from '@/components/chat/MessageItem';
import { MessageComposer } from '@/components/chat/MessageComposer';
import { KbSelector } from '@/components/chat/KbSelector';
import { MicSelector } from '@/components/chat/MicSelector';
import { PersonaSelector } from '@/components/chat/PersonaSelector';
import { ChatBackgroundPicker } from '@/components/chat/ChatBackgroundPicker';
import { CallBar } from '@/components/voice/CallBar';
import { useVoiceConversation } from '@/voice/v3/useVoiceConversation';
import { chatApi } from '@/api/chat';
import { aiBizActionApi } from '@/api/aiBizAction';
import { usePersonaStore } from '@/stores/persona';
import { getActiveChatBackground, useChatBackgroundStore } from '@/stores/chatBackground';
import type { AiSession, ReferenceItem } from '@/types/api';
import { getVoiceAsrDisplayText, type VoiceServerMessageV3 } from '@/voice/v3/protocol';

const BIZ_KEYWORDS = [
  '派发', '派给', '安排任务', '布置工作', '交给', '分配任务', '指派', '安排给', '让他做', '让她做',
  '创建客户', '新增客户', '添加客户', '录入客户',
  '创建合同', '新建合同', '签合同', '录入合同',
  '创建工单', '新建工单', '提交工单', '报修',
  '安排会议', '创建日程', '新建日程', '添加提醒', '明天', '后天', '下周',
  '发起审批', '提交审批', '申请审批',
  '查询客户', '搜索客户', '客户列表', '哪些客户',
  '查询合同', '合同到期', '合同统计', '哪些合同',
  '查询工单', '我的工单', '工单状态', '待处理工单',
  '今天安排', '本周日程', '我的日程', '查询日程',
  '跟进记录', '添加跟进', '记录拜访', '记录沟通',
];

interface DoneEvent {
  messageId?: string;
  latencyMs?: number;
  tokenIn?: number;
  tokenOut?: number;
}

function safeParse<T>(s: string): T | null {
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}

function uid() {
  return `t_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

function parseKbIds(s?: string | null): string[] {
  if (!s) return [];
  return s.split(',').map((x) => x.trim()).filter(Boolean);
}

export default function ChatPage() {
  const { sessionId: routeSid } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [session, setSession] = useState<AiSession | null>(null);
  const [loadingSession, setLoadingSession] = useState(false);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [kbIds, setKbIds] = useState<string[]>([]);
  const personaId = usePersonaStore((s) => s.personaId);
  const setPersonaId = usePersonaStore((s) => s.setPersonaId);
  const backgroundSelection = useChatBackgroundStore((s) => s.selection);
  const customBackgrounds = useChatBackgroundStore((s) => s.customImages);
  const [inputDeviceId, setInputDeviceId] = useState<string | undefined>(() => {
    try {
      return localStorage.getItem('zwsk.voice.inputDeviceId') || undefined;
    } catch {
      return undefined;
    }
  });
  const [voicePartial, setVoicePartial] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const currentVoiceAssistantRef = useRef<string | undefined>();

  const persona = useMemo(() => getPersonaProfile(personaId), [personaId]);
  const activeBackground = useMemo(
    () => getActiveChatBackground(backgroundSelection, customBackgrounds),
    [backgroundSelection, customBackgrounds],
  );

  const scrollToBottom = useCallback(() => {
    requestAnimationFrame(() => {
      const el = scrollRef.current;
      if (!el) return;
      const root = el.querySelector('[data-radix-scroll-area-viewport]') as HTMLElement | null;
      if (root) root.scrollTop = root.scrollHeight;
    });
  }, []);

  useEffect(() => {
    if (!routeSid) {
      setSession(null);
      setTurns([]);
      setKbIds([]);
      return;
    }
    if (session && String(session.id) === routeSid) return;

    setSession(null);
    setTurns([]);
    setKbIds([]);

    let canceled = false;
    const load = async () => {
      setLoadingSession(true);
      try {
        const [s, msgs] = await Promise.all([chatApi.getSession(routeSid), chatApi.listMessages(routeSid)]);
        if (canceled) return;
        setSession(s);
        setKbIds(parseKbIds(s.kbIds));
        const savedPersonaId = getSessionPersonaId(s.id);
        if (savedPersonaId) setPersonaId(savedPersonaId);
        const list: ChatTurn[] = (msgs ?? [])
          .filter((m) => m.role === 'user' || m.role === 'assistant')
          .map((m) => ({
            id: String(m.id),
            role: m.role as 'user' | 'assistant',
            content: m.content || '',
            latencyMs: m.latencyMs,
            tokenIn: m.tokenIn,
            tokenOut: m.tokenOut,
            createTime: m.createTime,
          }));
        setTurns(list);
        scrollToBottom();
      } finally {
        if (!canceled) setLoadingSession(false);
      }
    };
    void load();
    return () => {
      canceled = true;
    };
  }, [routeSid, session?.id, scrollToBottom, setPersonaId]);

  const ensureSession = useCallback(
    async (firstQuestion: string): Promise<AiSession> => {
      if (session) return session;
      const s = await chatApi.createSession({
        title: firstQuestion.slice(0, 24).trim() || '新对话',
        sessionType: 'TEXT',
        kbIds,
      });
      setSession(s);
      storeSessionPersonaId(s.id, personaId);
      navigate(`/chat/${s.id}`, { replace: true });
      return s;
    },
    [session, kbIds, navigate, personaId],
  );

  const ensureSessionForVoice = useCallback(async (): Promise<AiSession> => {
    if (session) return session;
    const s = await chatApi.createSession({
      title: '语音会话',
      sessionType: 'VOICE',
      kbIds,
    });
    setSession(s);
    storeSessionPersonaId(s.id, personaId);
    navigate(`/chat/${s.id}`, { replace: true });
    return s;
  }, [session, kbIds, navigate, personaId]);

  const handleVoiceServer = useCallback((msg: VoiceServerMessageV3) => {
    switch (msg.type) {
      case 'ASR_PARTIAL':
        setVoicePartial(msg.text || '');
        break;
      case 'ASR_FINAL': {
        setVoicePartial('');
        const text = getVoiceAsrDisplayText(msg);
        if (text) {
          const userId = uid();
          const assistantId = uid();
          currentVoiceAssistantRef.current = assistantId;
          setTurns((prev) => [
            ...prev,
            { id: userId, role: 'user', content: text },
            { id: assistantId, role: 'assistant', content: '', pending: true },
          ]);
        }
        break;
      }
      case 'REFERENCES': {
        const aid = currentVoiceAssistantRef.current;
        if (!aid) break;
        const refs = (msg.references || []) as unknown as ReferenceItem[];
        setTurns((prev) => prev.map((t) => (t.id === aid ? { ...t, references: refs } : t)));
        break;
      }
      case 'LLM_DELTA': {
        const aid = currentVoiceAssistantRef.current;
        if (!aid) break;
        const delta = msg.text || '';
        setTurns((prev) =>
          prev.map((t) => (t.id === aid ? { ...t, content: t.content + delta, pending: false } : t)),
        );
        break;
      }
      case 'LLM_DONE': {
        const aid = currentVoiceAssistantRef.current;
        if (!aid) break;
        setTurns((prev) => prev.map((t) => (t.id === aid ? { ...t, pending: false } : t)));
        currentVoiceAssistantRef.current = undefined;
        break;
      }
      case 'ERROR': {
        const errMsg = msg.text || `code=${msg.code ?? '-'}`;
        toast.error(`语音错误：${errMsg}`);
        break;
      }
      default:
        break;
    }
  }, []);

  const v = useVoiceConversation({
    kbIds,
    personaId,
    mode: 'HANDS_FREE',
    autoRestart: true,
    silenceMs: 900,
    minRecordingMs: 700,
    maxRecordingMs: 60000,
    speechRms: 0.003,
    speechPeak: 0.012,
    inputDeviceId,
    onServer: handleVoiceServer,
    onAutoEnd: (reason) => {
      if (reason === 'maxLength') toast('单次发言超过 60 秒，已自动结束');
    },
  });

  useEffect(() => {
    v.setSessionId(session?.id);
  }, [session?.id, v]);

  useEffect(() => {
    if (v.inCall) scrollToBottom();
  }, [turns, voicePartial, v.inCall, scrollToBottom]);

  const onSubmit = async () => {
    const q = input.trim();
    if (!q || sending) return;
    setInput('');

    const userTurn: ChatTurn = { id: uid(), role: 'user', content: q };
    const aiTurn: ChatTurn = { id: uid(), role: 'assistant', content: '', pending: true };
    setTurns((prev) => [...prev, userTurn, aiTurn]);
    setSending(true);
    scrollToBottom();

    // AI 业务意图检测：如果包含业务关键词，走 AI 全业务动作流程
    const isBizIntent = BIZ_KEYWORDS.some(kw => q.includes(kw));
    if (isBizIntent) {
      try {
        const result = await aiBizActionApi.preview(q);
        const content = result.confirmMessage || result.summary || result.successMessage || '';
        setTurns((prev) =>
          prev.map((t) => t.id === aiTurn.id
            ? { ...t, content, pending: false, bizAction: result }
            : t
          ),
        );
      } catch (err: any) {
        const msg = err?.response?.data?.message || err?.message || '操作解析失败';
        setTurns((prev) =>
          prev.map((t) => t.id === aiTurn.id ? { ...t, content: msg, pending: false, error: msg } : t),
        );
      } finally {
        setSending(false);
        scrollToBottom();
      }
      return;
    }

    try {
      const sess = await ensureSession(q);
      const ctrl = new AbortController();
      abortRef.current = ctrl;

      let acc = '';
      let refs: ReferenceItem[] | undefined;
      let done: DoneEvent | undefined;

      await chatApi.streamCompletion(
        sess.id,
        { question: q, kbIds, personaId },
        {
          onEvent: (event, data) => {
            if (event === 'references') {
              refs = safeParse<ReferenceItem[]>(data) ?? [];
              setTurns((prev) =>
                prev.map((t) => (t.id === aiTurn.id ? { ...t, references: refs, pending: t.content === '' } : t)),
              );
            } else if (event === 'delta') {
              acc += data;
              setTurns((prev) =>
                prev.map((t) => (t.id === aiTurn.id ? { ...t, content: acc, pending: false } : t)),
              );
              scrollToBottom();
            } else if (event === 'data_table') {
              const table = safeParse<{ columns: string[]; rows: Record<string, unknown>[]; sql?: string; executionMs?: number }>(data);
              if (table) {
                setTurns((prev) =>
                  prev.map((t) => (t.id === aiTurn.id ? { ...t, dataTable: table, pending: false } : t)),
                );
                scrollToBottom();
              }
            } else if (event === 'chart') {
              const chartData = safeParse<{ type: 'bar' | 'line'; title?: string; data: { label: string; value: number }[] }>(data);
              if (chartData) {
                setTurns((prev) =>
                  prev.map((t) => (t.id === aiTurn.id ? { ...t, chart: chartData, pending: false } : t)),
                );
                scrollToBottom();
              }
            } else if (event === 'moderation') {
              const m = safeParse<{ sanitized?: string }>(data);
              const clean = m?.sanitized ?? '';
              acc = clean;
              setTurns((prev) =>
                prev.map((t) => (t.id === aiTurn.id ? { ...t, content: clean, pending: false } : t)),
              );
              toast.warning('内容已按安全策略调整');
            } else if (event === 'done') {
              done = safeParse<DoneEvent>(data) ?? {};
            } else if (event === 'error') {
              const e = safeParse<{ code?: number; message?: string }>(data);
              throw new Error(e?.message || '问答失败');
            }
          },
          onError: (err) => {
            const msg = (err as Error)?.message || '请求失败';
            setTurns((prev) =>
              prev.map((t) => (t.id === aiTurn.id ? { ...t, error: msg, pending: false } : t)),
            );
            toast.error(`问答失败：${msg}`);
          },
        },
        ctrl.signal,
      );

      if (done) {
        setTurns((prev) =>
          prev.map((t) =>
            t.id === aiTurn.id
              ? { ...t, pending: false, latencyMs: done!.latencyMs, tokenIn: done!.tokenIn, tokenOut: done!.tokenOut }
              : t,
          ),
        );
      }
    } finally {
      setSending(false);
      abortRef.current = null;
      scrollToBottom();
    }
  };

  const onCall = async () => {
    if (v.inCall) return;
    try {
      const sess = await ensureSessionForVoice();
      await v.startCall(sess.id);
    } catch (err) {
      toast.error((err as Error)?.message || '通话启动失败');
    }
  };

  const heading = useMemo(() => {
    if (session) return session.title || '未命名对话';
    return '新对话';
  }, [session]);

  return (
    <div className="relative flex h-full flex-col overflow-hidden bg-background">
      {activeBackground ? (
        <>
          <div
            className="pointer-events-none absolute inset-0 bg-cover bg-center bg-no-repeat"
            style={{ backgroundImage: activeBackground.cssImage }}
            aria-hidden="true"
          />
          <div
            className={cn(
              'pointer-events-none absolute inset-0 backdrop-blur-[1px]',
              activeBackground.overlayClass,
            )}
            aria-hidden="true"
          />
        </>
      ) : null}
      <div className="relative z-10 flex h-full flex-col">
        <div className="flex h-12 items-center justify-between border-b bg-background/80 px-4 backdrop-blur-xl">
          <div className="min-w-0">
            <h1 className="truncate text-sm font-medium">{heading}</h1>
          </div>
          <div className="flex items-center gap-2">
            <PersonaSelector
              value={personaId}
              to="/assistants"
            />
            <MicSelector
              value={inputDeviceId}
              onChange={(deviceId) => {
                setInputDeviceId(deviceId);
                try {
                  if (deviceId) localStorage.setItem('zwsk.voice.inputDeviceId', deviceId);
                  else localStorage.removeItem('zwsk.voice.inputDeviceId');
                } catch {}
              }}
              disabled={v.inCall}
            />
            <ChatBackgroundPicker />
            <KbSelector value={kbIds} onChange={setKbIds} />
          </div>
        </div>

        <ScrollArea className="flex-1 bg-transparent" ref={scrollRef as any}>
          {loadingSession ? (
            <div className="space-y-4 p-4">
              <Skeleton className="h-16 w-3/4" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-16 w-2/3" />
            </div>
          ) : turns.length === 0 && !voicePartial ? (
            <Welcome
              persona={persona}
              onPick={(t) => setInput(t)}
              onChangePersona={() => navigate('/assistants', { state: { from: location.pathname } })}
            />
          ) : (
            <div className="mx-auto max-w-3xl pb-32">
              {turns.map((t) => (
                <MessageItem key={t.id} turn={t} />
              ))}
              {v.recording && voicePartial ? (
                <div className="flex w-full justify-end gap-3 px-4 py-2">
                  <div className="max-w-[78%] rounded-2xl bg-muted/90 px-4 py-3 text-sm leading-7 text-muted-foreground shadow-sm backdrop-blur-md">
                    {voicePartial}
                    <span className="ml-1 inline-block h-3 w-1 animate-pulse-soft bg-current align-middle" />
                  </div>
                </div>
              ) : null}
            </div>
          )}
        </ScrollArea>

        {v.inCall ? (
          <CallBar
            inCall={v.inCall}
            voiceState={v.voiceState}
            recording={v.recording}
            volume={v.volume}
            sessionId={v.sessionId}
            kbCount={kbIds.length}
            onToggle={v.endCall}
            onInterrupt={v.interrupt}
            error={v.error}
          />
        ) : (
          <MessageComposer
            value={input}
            onChange={setInput}
            onSubmit={onSubmit}
            onAbort={() => abortRef.current?.abort()}
            onCall={onCall}
            loading={sending}
            hint={
              kbIds.length === 0 ? (
                <>未选择知识库，模型将使用通用知识作答</>
              ) : (
                <>已挂载 {kbIds.length} 个知识库参与检索</>
              )
            }
          />
        )}
      </div>
    </div>
  );
}

function Welcome({
  persona,
  onPick,
  onChangePersona,
}: {
  persona: AssistantPersona;
  onPick: (q: string) => void;
  onChangePersona: () => void;
}) {
  return (
    <div className="mx-auto flex h-full max-w-6xl flex-col justify-center px-6 py-12">
      <div className="grid gap-6 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border bg-background px-3 py-1 text-xs font-medium text-muted-foreground shadow-sm">
            <UserRound className="h-3.5 w-3.5" />
            当前助手界面
          </div>
          <div className="mt-5 flex items-center gap-4">
            <div
              className={cn(
                'flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-base font-semibold text-white shadow-sm',
                persona.accent,
              )}
            >
              {persona.initials}
            </div>
            <div className="min-w-0">
              <h2 className="truncate text-2xl font-semibold tracking-tight md:text-3xl">
                {persona.name}
              </h2>
              <p className="mt-1 text-sm text-muted-foreground">{persona.title}</p>
            </div>
          </div>
          <p className="mt-5 max-w-xl text-sm leading-7 text-muted-foreground">
            {persona.description}
          </p>
          <div className="mt-5 flex flex-wrap gap-2">
            <div className="inline-flex items-center gap-2 rounded-lg border bg-card px-3 py-2 text-xs text-muted-foreground shadow-sm">
              <Sparkles className="h-3.5 w-3.5" />
              {persona.tone}
            </div>
            <button
              type="button"
              onClick={onChangePersona}
              className="inline-flex items-center gap-2 rounded-lg border bg-card px-3 py-2 text-xs font-medium text-foreground shadow-sm transition hover:border-foreground/40 hover:bg-accent"
            >
              切换助手
              <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <div className="rounded-lg border bg-card p-4 shadow-sm">
          <div className="mb-3">
            <div className="mb-3 grid gap-2 sm:grid-cols-3">
              {persona.strengths.map((item) => (
                <div key={item} className="rounded-md border bg-background px-3 py-2 text-xs text-muted-foreground">
                  <BadgeCheck className="mb-1 h-3.5 w-3.5 text-emerald-600" />
                  {item}
                </div>
              ))}
            </div>
            <h3 className="text-sm font-semibold">可以从这里开始</h3>
            <p className="mt-1 text-xs text-muted-foreground">
              这些问题会按当前助手的任务风格发起
            </p>
          </div>
          <div className="grid gap-2">
            {persona.prompts.map((sample) => (
              <button
                key={sample}
                onClick={() => onPick(sample)}
                className="rounded-lg border bg-background p-3 text-left text-sm text-muted-foreground shadow-sm transition hover:border-foreground/40 hover:bg-accent hover:text-foreground"
              >
                {sample}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
