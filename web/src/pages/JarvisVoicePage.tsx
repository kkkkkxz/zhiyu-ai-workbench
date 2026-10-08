import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Activity,
  ArrowLeft,
  ChevronDown,
  Database,
  KeyRound,
  Mic,
  PhoneOff,
  Plus,
  Radio,
  RotateCcw,
  Search,
  ShieldCheck,
  Sparkles,
  Square,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';

import { voiceKeywordApi } from '@/api/voiceKeyword';
import { PersonaSelector } from '@/components/chat/PersonaSelector';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/stores/auth';
import { usePersonaStore } from '@/stores/persona';
import type { VoiceKeyword } from '@/types/api';
import { useVoiceConversation } from '@/voice/v3/useVoiceConversation';
import { listAudioInputDevices, probeMicrophoneLevel, type AudioInputDevice, type MicProbeResult } from '@/voice/v3/audio';
import { getVoiceAsrDisplayText, type VoiceReferenceV3, type VoiceServerMessageV3, type VoiceStateV3 } from '@/voice/v3/protocol';

interface Turn {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
}

const STATE_COPY: Record<VoiceStateV3, { label: string; hint: string }> = {
  IDLE: { label: '待命', hint: '点击核心开始语音对话' },
  LISTENING: { label: '聆听中', hint: '请直接说话，停顿后我会自动思考' },
  THINKING: { label: '推理中', hint: '正在检索全局向量库并组织答案' },
  SPEAKING: { label: '回应中', hint: 'AI 正在语音回答，可随时打断' },
  CLOSED: { label: '已离线', hint: '连接已关闭' },
};

const SPHERE_PARTICLE_COUNT = 1080;
const SPHERE_PARTICLES = Array.from({ length: SPHERE_PARTICLE_COUNT }, (_, i) => {
  const u = (i + 0.5) / SPHERE_PARTICLE_COUNT;
  const phi = Math.acos(1 - 2 * u);
  const theta = i * Math.PI * (3 - Math.sqrt(5));
  return {
    theta,
    phi,
    // 球放大后粒子也按比例加大，避免颗粒看起来稀疏纤细
    size: 0.58 + ((i * 17) % 17) / 16,
    twinkle: ((i * 29) % 100) / 100,
    speed: 0.62 + ((i * 11) % 23) / 52,
  };
});

const ORBIT_WORD = 'Oliver';
const TEXT_BANDS = [
  { count: 34, radius: 0.9, yScale: 0.3, tilt: -0.18, phase: 0, speed: 1, font: 0.92, alpha: 1 },
  { count: 28, radius: 0.98, yScale: 0.35, tilt: 0.22, phase: 0.8, speed: -0.72, font: 0.74, alpha: 0.72 },
  { count: 20, radius: 0.72, yScale: 0.22, tilt: -0.58, phase: 1.55, speed: 1.24, font: 0.66, alpha: 0.58 },
] as const;

function nowId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

const LOCAL_KEYWORD_STORAGE_KEY = 'zwsk.voice.keywords.local';
const KEYWORD_STOP_WORDS = new Set([
  '这个', '那个', '然后', '就是', '一下', '我们', '你们', '他们', '它们', '什么', '怎么', '如何', '可以', '是不是',
  '帮我', '给我', '请问', '关于', '进行', '一个', '一些', '需要', '现在', '今天', '明天', '问题', '内容', '资料',
  'the', 'and', 'for', 'with', 'that', 'this', 'what', 'how', 'why', 'can', 'you', 'your', 'our', 'please',
]);

function normalizeKeyword(raw: string | undefined | null) {
  const text = (raw || '')
    .trim()
    .replace(/^[\p{P}，。！？、；：‘’“”（）【】《》]+/u, '')
    .replace(/[\p{P}，。！？、；：‘’“”（）【】《》]+$/u, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (text.length < 2) return '';
  return text.slice(0, 64);
}

function extractKeywordsLocal(text: string, limit = 8) {
  const result: string[] = [];
  const seen = new Set<string>();
  const re = /[\u4e00-\u9fff]{2,8}|[A-Za-z][A-Za-z0-9_+.#-]{1,31}|[0-9]+(?:\.[0-9]+)?[%％]?/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(text)) && result.length < limit) {
    const keyword = normalizeKeyword(match[0]);
    const key = keyword.toLowerCase();
    if (!keyword || KEYWORD_STOP_WORDS.has(keyword) || KEYWORD_STOP_WORDS.has(key) || seen.has(key)) continue;
    seen.add(key);
    result.push(keyword);
  }
  return result;
}

function readLocalKeywords(): VoiceKeyword[] {
  try {
    const raw = localStorage.getItem(LOCAL_KEYWORD_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function writeLocalKeywords(list: VoiceKeyword[]) {
  try {
    localStorage.setItem(LOCAL_KEYWORD_STORAGE_KEY, JSON.stringify(list.slice(0, 200)));
  } catch {}
}

function upsertLocalKeywords(keywords: string[], sourceText?: string, sessionId?: string, turnId?: string, sourceType: 'AUTO' | 'MANUAL' = 'AUTO') {
  const list = readLocalKeywords();
  const now = new Date().toISOString();
  for (const raw of keywords) {
    const keyword = normalizeKeyword(raw);
    if (!keyword) continue;
    const idx = list.findIndex((item) => item.keyword.toLowerCase() === keyword.toLowerCase());
    if (idx >= 0) {
      list[idx] = {
        ...list[idx],
        sourceText: sourceText || list[idx].sourceText,
        sessionId: sessionId || list[idx].sessionId,
        turnId: turnId || list[idx].turnId,
        sourceType,
        hitCount: (list[idx].hitCount || 0) + 1,
        updateTime: now,
      };
    } else {
      list.unshift({
        id: `local-${Date.now()}-${Math.random().toString(16).slice(2)}`,
        tenantId: 'local',
        userId: 'local',
        sessionId,
        turnId,
        keyword,
        sourceType,
        sourceText,
        hitCount: 1,
        status: 1,
        createTime: now,
        updateTime: now,
      });
    }
  }
  list.sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime());
  writeLocalKeywords(list);
  return list;
}

function removeLocalKeyword(id: string) {
  const next = readLocalKeywords().filter((item) => item.id !== id);
  writeLocalKeywords(next);
  return next;
}

export default function JarvisVoicePage() {
  const navigate = useNavigate();
  const authenticated = useAuthStore((s) => s.isAuthenticated());
  const [turns, setTurns] = useState<Turn[]>([
    {
      id: 'welcome',
      role: 'system',
      content: '你好，我是行业 AI 知识助手。你可以向我咨询 AI 技术、RAG 知识库、企业知识库、智能体应用、私有化部署、内网大模型和行业落地方案。',
    },
  ]);
  const [partial, setPartial] = useState('');
  const [assistantDraft, setAssistantDraft] = useState('');
  const [references, setReferences] = useState<VoiceReferenceV3[]>([]);
  const [lastTtsText, setLastTtsText] = useState('');
  const [lastEvent, setLastEvent] = useState('系统待命');
  const [audioDevices, setAudioDevices] = useState<AudioInputDevice[]>([]);
  const [keywordOpen, setKeywordOpen] = useState(false);
  const [keywords, setKeywords] = useState<VoiceKeyword[]>([]);
  const [keywordQuery, setKeywordQuery] = useState('');
  const [manualKeyword, setManualKeyword] = useState('');
  const [keywordLoading, setKeywordLoading] = useState(false);
  const [keywordSaving, setKeywordSaving] = useState(false);
  const personaId = usePersonaStore((s) => s.personaId);
  const setPersonaId = usePersonaStore((s) => s.setPersonaId);
  const [selectedDeviceId, setSelectedDeviceId] = useState<string | undefined>(() => {
    try { return localStorage.getItem('zwsk.voice.inputDeviceId') || undefined; } catch { return undefined; }
  });
  const [probeRunning, setProbeRunning] = useState(false);
  const [probeResult, setProbeResult] = useState<MicProbeResult | null>(null);
  const assistantDraftRef = useRef('');
  const conversationScrollRef = useRef<HTMLDivElement | null>(null);

  const refreshKeywords = async (q = keywordQuery) => {
    setKeywordLoading(true);
    try {
      if (authenticated) {
        const list = await voiceKeywordApi.list({ q: q || undefined, limit: 100 });
        setKeywords(list ?? []);
      } else {
        const query = q.trim().toLowerCase();
        const list = readLocalKeywords()
          .filter((item) => !query || item.keyword.toLowerCase().includes(query))
          .slice(0, 100);
        setKeywords(list);
      }
    } catch {
      const query = q.trim().toLowerCase();
      setKeywords(readLocalKeywords().filter((item) => !query || item.keyword.toLowerCase().includes(query)).slice(0, 100));
    } finally {
      setKeywordLoading(false);
    }
  };

  useEffect(() => {
    void refreshKeywords('');
    // 登录状态只在首屏/账号切换时决定从后端还是本地读；搜索变化由输入框 onChange 手动刷新。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticated]);

  const recordHumanKeywords = async (text: string, turnId?: string) => {
    const sourceText = text.trim();
    if (!sourceText) return;

    if (!authenticated) {
      const localExtracted = extractKeywordsLocal(sourceText, 8);
      if (localExtracted.length === 0) return;
      const localList = upsertLocalKeywords(localExtracted, sourceText, undefined, turnId, 'AUTO');
      setKeywords(() => {
        const query = keywordQuery.trim().toLowerCase();
        return localList.filter((item) => !query || item.keyword.toLowerCase().includes(query)).slice(0, 100);
      });
      setLastEvent(`本地暂存关键词：${localExtracted.slice(0, 4).join('、')}`);
      return;
    }

    setLastEvent('AI 正在判断关键词…');
    try {
      const saved = await voiceKeywordApi.batch({
        // 自动记录时只提交原始人类发言，由后端调用 AI 判断真正关键词后再写库。
        keywords: [],
        sourceText,
        // 当前语音球入口走 public WebSocket，服务端会话属于 public 预算用户；
        // 关键词归当前登录用户，因此不强行关联 sessionId，避免跨用户归属校验失败。
        turnId,
      });
      const latest = await voiceKeywordApi.list({ q: keywordQuery || undefined, limit: 100 });
      setKeywords(saved.length > 0
        ? saved.concat(latest.filter((item) => !saved.some((s) => s.id === item.id)))
        : latest);
      setLastEvent(saved.length > 0
        ? `AI 已记录关键词：${saved.slice(0, 4).map((item) => item.keyword).join('、')}`
        : 'AI 判断本轮没有需要记录的关键词');
    } catch (err) {
      console.warn('[VoiceKeyword] AI persist failed', err);
      setLastEvent('AI 关键词判断失败，未写入数据库');
    }
  };

  const addManualKeyword = async () => {
    const keyword = normalizeKeyword(manualKeyword);
    if (!keyword) {
      toast.warning('请输入至少 2 个字符的关键词');
      return;
    }
    setKeywordSaving(true);
    try {
      if (authenticated) {
        await voiceKeywordApi.upsert({ keyword, sourceText: '手动添加' });
        await refreshKeywords(keywordQuery);
      } else {
        setKeywords(upsertLocalKeywords([keyword], '手动添加', undefined, undefined, 'MANUAL'));
      }
      setManualKeyword('');
      setKeywordOpen(true);
      toast.success(`已记录关键词「${keyword}」`);
    } finally {
      setKeywordSaving(false);
    }
  };

  const deleteKeyword = async (item: VoiceKeyword) => {
    try {
      if (authenticated && !String(item.id).startsWith('local-')) {
        await voiceKeywordApi.remove(String(item.id));
        await refreshKeywords(keywordQuery);
      } else {
        setKeywords(removeLocalKeyword(String(item.id)));
      }
      toast.success('关键词已删除');
    } catch {
      // api 拦截器已提示
    }
  };

  useEffect(() => {
    let alive = true;
    const loadDevices = async () => {
      try {
        const devices = await listAudioInputDevices();
        if (!alive) return;
        setAudioDevices(devices);
        if (selectedDeviceId && devices.length > 0 && !devices.some((device) => device.deviceId === selectedDeviceId)) {
          setSelectedDeviceId(undefined);
          try { localStorage.removeItem('zwsk.voice.inputDeviceId'); } catch {}
        }
      } catch (err) {
        console.warn('[Voice-V3] failed to list audio input devices', err);
      }
    };
    void loadDevices();
    navigator.mediaDevices?.addEventListener?.('devicechange', loadDevices);
    return () => {
      alive = false;
      navigator.mediaDevices?.removeEventListener?.('devicechange', loadDevices);
    };
  }, [selectedDeviceId]);

  useEffect(() => {
    const raf = window.requestAnimationFrame(() => {
      const el = conversationScrollRef.current;
      if (!el) return;
      el.scrollTop = el.scrollHeight;
    });
    return () => window.cancelAnimationFrame(raf);
  }, [turns, partial, assistantDraft, lastTtsText]);

  const chooseInputDevice = (deviceId: string) => {
    const value = deviceId || undefined;
    setSelectedDeviceId(value);
    setProbeResult(null);
    try {
      if (value) localStorage.setItem('zwsk.voice.inputDeviceId', value);
      else localStorage.removeItem('zwsk.voice.inputDeviceId');
    } catch {}
    setLastEvent(value ? '已切换麦克风，重新启动通话后生效' : '已切换到系统默认麦克风');
  };

  const choosePersona = (next: string) => {
    setPersonaId(next || 'general');
    setLastEvent('已切换回答人设，下一轮对话生效');
  };

  const voice = useVoiceConversation({
    publicAccess: !authenticated,
    globalKb: true,
    personaId,
    mode: 'HANDS_FREE',
    autoRestart: true,
    silenceMs: 900,
    minRecordingMs: 700,
    maxRecordingMs: 60000,
    inputDeviceId: selectedDeviceId,
    speechRms: 0.003,
    speechPeak: 0.012,
    onAutoEnd: (reason) => {
      setLastEvent(reason === 'silence' ? '检测到停顿，开始处理' : '已达到单次最长录音，开始处理');
    },
    onError: (err) => toast.error(err.message || '语音连接异常'),
    onServer: (msg: VoiceServerMessageV3) => {
      switch (msg.type) {
        case 'SESSION':
          setLastEvent(msg.sessionId ? `会话 ${msg.sessionId} 已建立` : '会话已建立');
          break;
        case 'STATE':
          if (msg.state) setLastEvent(STATE_COPY[msg.state as VoiceStateV3]?.hint ?? msg.state);
          break;
        case 'ASR_PARTIAL':
          setPartial(msg.text || '');
          break;
        case 'ASR_FINAL': {
          const text = getVoiceAsrDisplayText(msg);
          setPartial('');
          assistantDraftRef.current = '';
          setAssistantDraft('');
          // 用户说完话进入新一轮：清空上一轮的字幕，避免和新答案叠加
          setLastTtsText('');
          if (text) {
            setTurns((prev) => [
              ...prev.slice(-7),
              { id: nowId('u'), role: 'user', content: text },
            ]);
            void recordHumanKeywords(text, msg.turnId);
          }
          break;
        }
        case 'REFERENCES':
          setReferences(msg.references || []);
          setLastEvent(`已命中 ${msg.references?.length ?? 0} 条参考片段`);
          break;
        case 'LLM_DELTA': {
          const delta = msg.text || '';
          assistantDraftRef.current += delta;
          setAssistantDraft(assistantDraftRef.current);
          break;
        }
        case 'LLM_DONE': {
          const finalText = assistantDraftRef.current.trim();
          if (finalText) {
            setTurns((prev) => [
              ...prev.slice(-7),
              { id: nowId('a'), role: 'assistant', content: finalText },
            ]);
            // 字幕内容用 LLM 完整答案做整轮稳定显示，避免每句 TTS_BEGIN 都
            // 替换 lastTtsText 触发 ticker 的 key={text} 重挂载导致动画跳动。
            // ticker 的显隐由 voiceState === 'SPEAKING' 控制（见 TickerStrip）。
            setLastTtsText(finalText);
          }
          assistantDraftRef.current = '';
          setAssistantDraft('');
          break;
        }
        // TTS_BEGIN：把当前已生成的 LLM 完整内容快照贴给字幕。
        // 这样 AI 一开口字幕就出现；后续 TTS_BEGIN（流式分句下每句一次）也只是
        // 把字幕"扩展"成更长的版本，配合 TickerStrip 去掉 key={text} 后 CSS
        // animation 不会重启，width:max-content 自动适应新宽度，视觉是平滑的。
        case 'TTS_BEGIN': {
          const snapshot = assistantDraftRef.current.trim();
          if (snapshot) setLastTtsText(snapshot);
          break;
        }
        case 'TTS_END':
          break;
        case 'ERROR':
          setTurns((prev) => [
            ...prev.slice(-7),
            { id: nowId('e'), role: 'system', content: msg.text || `错误 code=${msg.code}` },
          ]);
          break;
        default:
          break;
      }
    },
  });

  const runMicProbe = async () => {
    if (voice.inCall) {
      toast.warning('请先结束通话，再测试或切换麦克风');
      return;
    }
    setProbeRunning(true);
    setProbeResult(null);
    setLastEvent('请对着麦克风说话，正在测试输入电平...');
    try {
      const result = await probeMicrophoneLevel(selectedDeviceId, 3200);
      setProbeResult(result);
      setLastEvent(result.hasSignal
        ? `麦克风测试通过：peak=${result.maxPeak.toFixed(4)}`
        : `麦克风仍为静音：peak=${result.maxPeak.toFixed(4)}，请换输入设备或检查系统静音`);
      if (result.hasSignal) toast.success('检测到麦克风声音');
      else toast.warning('未检测到麦克风声音，请尝试切换输入设备');
    } catch (err) {
      const msg = (err as Error)?.message || '麦克风测试失败';
      setLastEvent(msg);
      toast.error(msg);
    } finally {
      setProbeRunning(false);
    }
  };

  const stateCopy = STATE_COPY[voice.voiceState] ?? STATE_COPY.IDLE;
  const energy = Math.max(
    voice.voiceState === 'SPEAKING' ? voice.playbackVolume : 0,
    voice.recording ? voice.volume * 1.2 : 0,
    voice.voiceState === 'THINKING' ? 0.12 : 0,
  );
  const modeLabel = useMemo(() => {
    if (voice.status === 'connecting') return 'CONNECTING';
    if (voice.status === 'open') return 'ONLINE';
    if (voice.status === 'error') return 'ERROR';
    return 'STANDBY';
  }, [voice.status]);

  const toggleCall = async () => {
    if (voice.inCall) {
      voice.endCall();
      setLastEvent('语音通话已结束');
      return;
    }
    try {
      await voice.startCall();
      setLastEvent('全局语音通话已启动');
    } catch (err) {
      const msg = (err as Error)?.message || '无法启动语音通话';
      toast.error(msg);
      setLastEvent(msg);
    }
  };

  const reset = () => {
    assistantDraftRef.current = '';
    setAssistantDraft('');
    setPartial('');
    setReferences([]);
    setLastTtsText('');
    setTurns([
      {
        id: nowId('sys'),
        role: 'system',
        content: 'AI 已重置。点击核心即可继续基于公开全局向量库对话。',
      },
    ]);
    voice.resetConversation();
  };

  return (
    <div className="relative h-[100dvh] max-h-[100dvh] overflow-hidden bg-[#020617] text-slate-100">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_18%,rgba(34,211,238,0.26),transparent_32%),radial-gradient(circle_at_14%_18%,rgba(59,130,246,0.22),transparent_28%),radial-gradient(circle_at_86%_72%,rgba(168,85,247,0.22),transparent_28%),linear-gradient(180deg,rgba(15,23,42,0.10),rgba(2,6,23,0.96))]" />
      <div className="pointer-events-none absolute inset-0 opacity-[0.18] [background-image:linear-gradient(rgba(148,163,184,0.23)_1px,transparent_1px),linear-gradient(90deg,rgba(148,163,184,0.23)_1px,transparent_1px)] [background-size:54px_54px]" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,transparent_0,transparent_48%,rgba(2,6,23,0.68)_78%,rgba(2,6,23,0.96)_100%)]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-48 bg-gradient-to-b from-cyan-400/14 to-transparent" />
      <div className="pointer-events-none absolute left-1/2 top-[52%] h-[min(72vw,760px)] w-[min(72vw,760px)] -translate-x-1/2 -translate-y-1/2 rounded-full border border-cyan-200/10 bg-cyan-300/[0.025] shadow-[0_0_120px_rgba(34,211,238,0.16)]" />

      <header className="relative z-10 grid h-14 shrink-0 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2 px-3 py-2 lg:gap-3 lg:px-8">
        <button
          type="button"
          onClick={() => navigate('/chat')}
          className="group inline-flex items-center gap-1.5 rounded-full border border-cyan-200/20 bg-white/[0.055] px-2.5 py-1.5 text-xs text-slate-300 shadow-[inset_0_1px_0_rgba(255,255,255,0.08),0_0_28px_rgba(34,211,238,0.08)] backdrop-blur-2xl transition hover:border-cyan-200/50 hover:bg-cyan-200/[0.08] hover:text-white sm:px-4 sm:py-2 sm:text-sm"
        >
          <ArrowLeft className="h-4 w-4 transition group-hover:-translate-x-0.5" />
          <span className="hidden min-[380px]:inline">返回控制台</span>
        </button>
        <div className="flex min-w-0 items-center justify-center gap-2 lg:gap-3">
          <Sparkles className="hidden h-4 w-4 shrink-0 text-cyan-200 sm:block" />
          <h1 className="truncate text-base font-semibold tracking-[-0.02em] text-white sm:text-lg lg:text-xl">
            展望 AI 语音中枢 · 企业专有助手
          </h1>
        </div>
        <div className="flex items-center gap-2 rounded-full border border-cyan-200/20 bg-white/[0.055] px-2.5 py-1.5 shadow-[inset_0_1px_0_rgba(255,255,255,0.08),0_0_28px_rgba(34,211,238,0.08)] backdrop-blur-2xl sm:gap-3 sm:px-4 sm:py-2">
          <span className="relative flex h-2.5 w-2.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-cyan-300 opacity-70" />
            <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-cyan-300" />
          </span>
          <span className="font-mono text-[10px] tracking-[0.2em] text-cyan-100 sm:text-xs sm:tracking-[0.28em]">{modeLabel}</span>
        </div>
      </header>

      {/* 主舞台：flex-col 自上而下排列。
          顺序：球 → 字幕条 → 按钮组 → 三个状态框（贴底）
          - 球用 max-h-[54vh] 限制最大高度，矮屏自动收敛
          - 中间元素全部 shrink-0 不被压缩
          - 状态框用 mt-auto 推到底部 */}
      <main className="relative z-10 flex h-[calc(100dvh-56px)] min-h-0 flex-col items-center px-2 pb-2 pt-16 lg:px-6 lg:pb-3 lg:pt-4">
        <button
          type="button"
          onClick={() => void toggleCall()}
          className="group relative flex aspect-square h-[180px] max-h-[30vh] max-w-full shrink-0 items-center justify-center rounded-full outline-none transition-transform before:absolute before:inset-[-8px] before:rounded-full before:border before:border-cyan-100/10 before:bg-[radial-gradient(circle_at_50%_44%,rgba(255,255,255,0.055),rgba(34,211,238,0.022)_36%,transparent_64%)] before:shadow-[0_0_30px_rgba(34,211,238,0.1),inset_0_0_26px_rgba(255,255,255,0.028)] before:opacity-80 after:absolute after:inset-[-12px] after:rounded-full after:border after:border-sky-200/7 after:bg-[conic-gradient(from_145deg,transparent,rgba(56,189,248,0.1),transparent_30%,rgba(20,184,166,0.075),transparent_58%,rgba(99,102,241,0.055),transparent)] after:opacity-36 after:blur-[0.3px] hover:scale-[1.012] focus-visible:ring-2 focus-visible:ring-cyan-200/60 min-[380px]:h-[210px] sm:h-[320px] sm:max-h-[54vh] md:h-[400px] lg:h-[480px] xl:h-[560px] 2xl:h-[640px]"
          aria-label={voice.inCall ? '结束 AI 语音通话' : '启动 AI 语音通话'}
        >
          <ParticleVoiceSphere energy={energy} active={voice.inCall} stateLabel={voice.inCall ? stateCopy.label : '点击启动'} />
        </button>

        {/* 字幕条：紧贴球下方 */}
        <div className="mt-2 flex w-full shrink-0 justify-center px-1 lg:mt-4">
          <TickerStrip
            voiceState={voice.voiceState}
            ttsText={lastTtsText}
          />
        </div>

        {/* 按钮组：字幕下方 */}
        <div className="mt-2 flex shrink-0 flex-wrap items-center justify-center gap-2 lg:mt-4 lg:gap-3">
          <Button
            type="button"
            onClick={() => void toggleCall()}
            className={cn(
              'h-9 rounded-full px-4 text-xs shadow-[0_0_34px_rgba(34,211,238,0.24),inset_0_1px_0_rgba(255,255,255,0.18)] lg:h-11 lg:px-5 lg:text-sm',
              voice.inCall
                ? 'bg-gradient-to-r from-rose-500 to-orange-400 text-white hover:from-rose-400 hover:to-orange-300'
                : 'bg-gradient-to-r from-cyan-200 via-sky-200 to-emerald-200 text-slate-950 hover:from-cyan-100 hover:to-emerald-100',
            )}
          >
            {voice.inCall ? <PhoneOff className="mr-2 h-4 w-4" /> : <Mic className="mr-2 h-4 w-4" />}
            {voice.inCall ? '结束通话' : '启动语音'}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={voice.interrupt}
            disabled={!voice.inCall || (voice.voiceState !== 'SPEAKING' && voice.voiceState !== 'THINKING')}
            className="h-9 rounded-full border-cyan-200/25 bg-white/[0.055] px-4 text-xs text-cyan-50 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] hover:bg-cyan-200/10 lg:h-11 lg:px-5 lg:text-sm"
          >
            <Square className="mr-2 h-4 w-4" />
            打断
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={reset}
            className="h-9 rounded-full border-cyan-200/25 bg-white/[0.055] px-4 text-xs text-cyan-50 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] hover:bg-cyan-200/10 lg:h-11 lg:px-5 lg:text-sm"
          >
            <RotateCcw className="mr-2 h-4 w-4" />
            重置
          </Button>
        </div>

        {/* 三个状态框：mt-auto 推到底部，矮屏自动隐藏避免挤压 */}
        <div className="mt-auto grid w-full max-w-3xl shrink-0 grid-cols-3 gap-1.5 pt-2 text-[10px] text-slate-300 lg:gap-3 lg:pt-4 lg:text-xs [@media(max-height:620px)]:hidden">
          <Metric icon={<Radio className="h-4 w-4" />} label="状态" value={stateCopy.label} />
          <Metric icon={<Database className="h-4 w-4" />} label="检索" value={`全局可读库 · ${references.length} 片段`} />
          <Metric icon={<ShieldCheck className="h-4 w-4" />} label="安全" value="输出门禁已启用" />
        </div>
      </main>

      {/* 右上角悬浮 HUD：以球体为中心的极简控制台 */}
      <div className="pointer-events-none absolute right-2 top-16 z-20 flex max-h-64 w-28 flex-col gap-0.5 overflow-y-auto lg:right-5 lg:top-20 lg:max-h-[calc(100dvh-5.5rem)] lg:w-[320px] lg:gap-2 lg:pr-1">
        <KeywordDrawer
          open={keywordOpen}
          onOpenChange={setKeywordOpen}
          keywords={keywords}
          query={keywordQuery}
          onQueryChange={(value) => {
            setKeywordQuery(value);
            void refreshKeywords(value);
          }}
          manualKeyword={manualKeyword}
          onManualKeywordChange={setManualKeyword}
          loading={keywordLoading}
          saving={keywordSaving}
          authenticated={authenticated}
          onAdd={() => void addManualKeyword()}
          onDelete={(item) => void deleteKeyword(item)}
        />

        {/* 实时对话流 · 电平 */}
        <div className="pointer-events-auto rounded-lg border border-cyan-200/20 bg-slate-950/75 p-1 shadow-[0_0_32px_rgba(34,211,238,0.10),inset_0_1px_0_rgba(255,255,255,0.06)] backdrop-blur-2xl lg:rounded-2xl lg:p-3 lg:shadow-[0_0_54px_rgba(15,23,42,0.62),inset_0_1px_0_rgba(255,255,255,0.06)]">
          <div className="mb-0.5 flex items-center justify-between lg:mb-2">
            <div>
              <p className="font-mono text-[5px] uppercase tracking-[0.06em] text-cyan-200/70 lg:text-[10px] lg:tracking-[0.28em]">Telemetry</p>
              <h2 className="truncate text-[7px] font-semibold text-white lg:mt-0.5 lg:text-sm">实时对话流</h2>
            </div>
            <Activity className="h-3 w-3 text-cyan-200 lg:h-4 lg:w-4" />
          </div>
          <div className="rounded-md border border-cyan-300/10 bg-white/[0.03] p-0.5 lg:rounded-xl lg:p-2.5">
            <div className="mb-0.5 flex items-center justify-between gap-1 text-[6px] text-slate-400 lg:mb-1.5 lg:gap-2 lg:text-[11px]">
              <span className="truncate">{stateCopy.hint}</span>
              {voice.sessionId ? (
                <span className="shrink-0 font-mono text-[7px] text-cyan-200/70 lg:text-[10px]">#{voice.sessionId.slice(-6)}</span>
              ) : null}
            </div>
            <Level label="MIC" value={voice.volume} active={voice.recording} />
            <Level label="AI" value={voice.playbackVolume} active={voice.voiceState === 'SPEAKING'} />
            {voice.error ? <p className="mt-1 text-[7px] leading-3 text-rose-300 lg:mt-2 lg:text-[11px]">⚠ {voice.error}</p> : null}
            <p className="hidden lg:mt-1.5 lg:block lg:max-h-7 lg:overflow-hidden lg:text-[10px] lg:text-cyan-100/70">{lastEvent}</p>
          </div>
        </div>

        {/* 回答人设 */}
        <div className="pointer-events-auto rounded-lg border border-cyan-200/20 bg-slate-950/75 p-1 text-xs text-slate-300 shadow-[0_0_32px_rgba(34,211,238,0.10),inset_0_1px_0_rgba(255,255,255,0.06)] backdrop-blur-2xl lg:rounded-2xl lg:p-3 lg:shadow-[0_0_54px_rgba(15,23,42,0.62),inset_0_1px_0_rgba(255,255,255,0.06)]">
          <div className="lg:mb-2">
            <div className="min-w-0">
              <p className="font-mono text-[6px] uppercase tracking-[0.08em] text-cyan-200/70 lg:text-[10px] lg:tracking-[0.22em]">Persona</p>
              <p className="hidden truncate text-slate-400 lg:mt-0.5 lg:block lg:text-[10px]">下一轮生效</p>
            </div>
          </div>
          <PersonaSelector
            value={personaId}
            onChange={choosePersona}
            dark
            loadRemote={authenticated}
            compact
          />
        </div>

        {/* 麦克风输入 */}
        <div className="pointer-events-auto rounded-lg border border-cyan-200/20 bg-slate-950/75 p-1 text-xs text-slate-300 shadow-[0_0_32px_rgba(34,211,238,0.10),inset_0_1px_0_rgba(255,255,255,0.06)] backdrop-blur-2xl lg:rounded-2xl lg:p-3 lg:shadow-[0_0_54px_rgba(15,23,42,0.62),inset_0_1px_0_rgba(255,255,255,0.06)]">
          <div className="flex items-center justify-between gap-1 lg:mb-2 lg:gap-3">
            <div className="min-w-0">
              <p className="font-mono text-[6px] uppercase tracking-[0.08em] text-cyan-200/70 lg:text-[10px] lg:tracking-[0.22em]">Audio Input</p>
              <p className="hidden truncate text-slate-400 lg:mt-0.5 lg:block lg:text-[10px]">重启生效</p>
            </div>
            <Button
              type="button"
              variant="outline"
              onClick={() => void runMicProbe()}
              disabled={probeRunning || voice.inCall}
              className="h-4 shrink-0 rounded-full border-cyan-300/30 bg-white/[0.04] px-1 text-[6px] text-cyan-50 hover:bg-cyan-300/10 lg:h-7 lg:px-2.5 lg:text-[11px]"
            >
              {probeRunning ? '测试中' : '测试'}
            </Button>
          </div>
          <select
            value={selectedDeviceId ?? ''}
            onChange={(event) => chooseInputDevice(event.target.value)}
            disabled={voice.inCall}
            className="h-5 w-full rounded-md border border-cyan-300/30 bg-slate-950/85 px-1 text-[7px] font-medium text-cyan-50 outline-none shadow-[inset_0_0_14px_rgba(34,211,238,0.08)] focus:border-cyan-200 lg:h-9 lg:rounded-xl lg:px-2.5 lg:text-[12px]"
          >
            <option value="">系统默认麦克风</option>
            {audioDevices.map((device) => (
              <option key={device.deviceId || device.label} value={device.deviceId}>
                {device.label}
              </option>
            ))}
          </select>
          {probeResult ? (
            <div className={cn(
              'mt-0.5 rounded-md border p-1 text-[7px] leading-3 lg:mt-1.5 lg:rounded-lg lg:p-1.5 lg:text-[10px] lg:leading-4',
              probeResult.hasSignal
                ? 'border-emerald-300/20 bg-emerald-300/10 text-emerald-50'
                : 'border-rose-300/20 bg-rose-300/10 text-rose-50',
            )}>
              <p>{probeResult.hasSignal ? '已检测到输入声音' : '未检测到输入声音'} · peak={probeResult.maxPeak.toFixed(4)}</p>
            </div>
          ) : null}
          {voice.inCall ? <p className="mt-0.5 text-[7px] text-amber-200/80 lg:mt-1 lg:text-[10px]">通话中不能切换设备</p> : null}
        </div>
      </div>
    </div>
  );
}

function TickerStrip({
  voiceState,
  ttsText,
}: {
  voiceState: VoiceStateV3;
  ttsText: string;
}) {
  // 严格只显示 AI 真正在朗读的内容：仅 SPEAKING 状态 + 有 TTS 文本时才滚动。
  const text = (ttsText || '').replace(/\s+/g, ' ').trim();
  const active = voiceState === 'SPEAKING' && text.length > 0;

  // duration 必须**全程恒定**，否则浏览器看到 animation-duration 变化会按新值
  // 重算当前 transform 位置 → 视觉是"跳一下"。
  // 流式分句下 TTS_BEGIN 会多次扩展字幕（首句 → 首两句 → 完整答案），如果让
  // duration 跟随 text 长度计算就会反复跳。
  //
  // 折衷：duration 在字幕首次激活（active 由 false → true，即每轮 AI 开口的
  // 那一刻）时**按当时已有的 text 长度算一次后锁定**，后续 text 扩展不更新。
  // 副作用：text 后续扩展会让"线速度"略变快（同 duration 滚更长一份字幕），
  // 但视觉上 transform 位置严格连续，不会跳——这正是用户想要的"轮播图"。
  const [duration, setDuration] = useState('80s');
  const wasActiveRef = useRef(false);
  useEffect(() => {
    if (active && !wasActiveRef.current) {
      // 第一次有字幕时基于当前长度估算：每字 1.0s，区间 60~180s。
      // 即一份字幕滚完最少 60 秒（短答案也走得很慢，能稳定看清每个字），
      // 长答案最多 180 秒（3 分钟一圈，避免眼花）。
      // assumedLen 最低按 80 字算，避免首句很短时 duration 抖到下限。
      const len = Math.max(80, text.length);
      const seconds = Math.max(60, Math.min(180, len * 1.0));
      setDuration(`${seconds}s`);
    }
    wasActiveRef.current = active;
  }, [active, text]);

  return (
    <div className="w-full max-w-3xl px-1">
      <div className="mb-1 flex items-center justify-between font-mono text-[10px] uppercase tracking-[0.28em]">
        <div className={cn('flex items-center gap-2', active ? 'text-emerald-200/85' : 'text-cyan-200/35')}>
          <span className="relative flex h-2 w-2">
            {active ? (
              <>
                <span className="absolute inset-0 animate-ping rounded-full bg-emerald-300/70" />
                <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-300 shadow-[0_0_14px_rgba(94,234,212,0.85)]" />
              </>
            ) : (
              <span className="relative inline-flex h-2 w-2 rounded-full bg-cyan-300/30" />
            )}
          </span>
          <span>{active ? 'AI · SPEAKING' : 'AI · IDLE'}</span>
        </div>
        <span className="text-cyan-200/30">SCROLL · MARQUEE</span>
      </div>
      <div className="voice-ticker h-9 rounded-full border border-cyan-200/20 bg-slate-950/70 shadow-[0_0_34px_rgba(34,211,238,0.08),inset_0_0_28px_rgba(34,211,238,0.12),inset_0_1px_0_rgba(255,255,255,0.06)] backdrop-blur-xl lg:h-10">
        {active ? (
          <div
            className="voice-ticker-track h-full items-center text-[12px] tracking-[0.04em] lg:text-[13px]"
            style={{ ['--ticker-duration' as string]: duration } as React.CSSProperties}
            // 故意不加 key={text}：流式分句下 TTS_BEGIN 会多次扩展字幕内容，
            // 加 key 会让 div 重挂载、动画从头开始 → 视觉是"跳动"。
            // 去掉 key 后 width:max-content 会平滑适应新宽度，已滚动比例保持。
          >
            <span className="voice-ticker-item flex h-full items-center gap-3 font-medium text-emerald-100">
              <span className="font-mono text-[10px] uppercase tracking-[0.32em] text-emerald-200/70">▸ AI · SPEAKING</span>
              <span className="text-emerald-300/40">/</span>
              <span>{text}</span>
            </span>
            <span className="voice-ticker-item flex h-full items-center gap-3 font-medium text-emerald-100" aria-hidden>
              <span className="font-mono text-[10px] uppercase tracking-[0.32em] text-emerald-200/70">▸ AI · SPEAKING</span>
              <span className="text-emerald-300/40">/</span>
              <span>{text}</span>
            </span>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function KeywordDrawer({
  open,
  onOpenChange,
  keywords,
  query,
  onQueryChange,
  manualKeyword,
  onManualKeywordChange,
  loading,
  saving,
  authenticated,
  onAdd,
  onDelete,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  keywords: VoiceKeyword[];
  query: string;
  onQueryChange: (value: string) => void;
  manualKeyword: string;
  onManualKeywordChange: (value: string) => void;
  loading: boolean;
  saving: boolean;
  authenticated: boolean;
  onAdd: () => void;
  onDelete: (item: VoiceKeyword) => void;
}) {
  const visible = keywords.slice(0, 80);
  return (
    <section className="pointer-events-auto overflow-hidden rounded-lg border border-cyan-300/15 bg-slate-950/70 shadow-[0_0_24px_rgba(15,23,42,0.42)] backdrop-blur-xl lg:rounded-2xl lg:shadow-[0_0_44px_rgba(15,23,42,0.58)]">
      <button
        type="button"
        onClick={() => onOpenChange(!open)}
        className="flex w-full items-center justify-between gap-1 px-1 py-1 text-left transition hover:bg-cyan-300/[0.04] lg:gap-3 lg:px-3 lg:py-2.5"
      >
        <div className="flex min-w-0 items-center gap-1 lg:gap-2">
          <div className="flex h-4 w-4 shrink-0 items-center justify-center rounded-md border border-cyan-300/20 bg-cyan-300/10 text-cyan-100 lg:h-8 lg:w-8 lg:rounded-xl">
            <KeyRound className="h-2.5 w-2.5 lg:h-4 lg:w-4" />
          </div>
          <div className="min-w-0">
            <p className="font-mono text-[5px] uppercase tracking-[0.06em] text-cyan-200/70 lg:text-[10px] lg:tracking-[0.22em]">Keywords</p>
            <h2 className="truncate text-[7px] font-semibold text-white lg:text-sm">关键词记录</h2>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-1 lg:gap-2">
          <span className="rounded-full border border-cyan-300/20 bg-cyan-300/10 px-1 py-0.5 font-mono text-[6px] text-cyan-100 lg:px-2 lg:text-[10px]">
            {keywords.length}
          </span>
          <ChevronDown className={cn('h-3 w-3 text-cyan-100/80 transition-transform lg:h-4 lg:w-4', open ? 'rotate-180' : 'rotate-0')} />
        </div>
      </button>

      <div
        className={cn(
          'grid transition-[grid-template-rows,opacity] duration-300 ease-out',
          open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0',
        )}
      >
        <div className="min-h-0 overflow-hidden">
          <div className="space-y-1 border-t border-cyan-300/10 p-1 lg:space-y-2 lg:p-3">
            <div className="flex items-center gap-1 rounded-md border border-cyan-300/15 bg-white/[0.035] px-1 py-1 lg:gap-2 lg:rounded-xl lg:px-2.5 lg:py-2">
              <Search className="h-2.5 w-2.5 shrink-0 text-cyan-200/65 lg:h-3.5 lg:w-3.5" />
              <input
                value={query}
                onChange={(event) => onQueryChange(event.target.value)}
                placeholder="搜索"
                className="h-4 min-w-0 flex-1 bg-transparent text-[7px] text-cyan-50 outline-none placeholder:text-[6px] placeholder:text-slate-500 lg:h-5 lg:text-[12px]"
              />
            </div>

            <div className="flex items-center gap-1 lg:gap-2">
              <input
                value={manualKeyword}
                onChange={(event) => onManualKeywordChange(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') onAdd();
                }}
                placeholder="补充"
                className="h-5 min-w-0 flex-1 rounded-md border border-cyan-300/15 bg-white/[0.035] px-1 text-[7px] text-cyan-50 outline-none placeholder:text-[6px] placeholder:text-slate-500 focus:border-cyan-300/45 lg:h-8 lg:rounded-xl lg:px-2.5 lg:text-[12px]"
              />
              <Button
                type="button"
                onClick={onAdd}
                disabled={saving}
                className="h-5 shrink-0 rounded-md bg-cyan-300 px-1 text-[7px] text-slate-950 hover:bg-cyan-200 lg:h-8 lg:rounded-xl lg:px-2.5 lg:text-[11px]"
              >
                <Plus className="mr-0.5 h-2.5 w-2.5 lg:mr-1 lg:h-3.5 lg:w-3.5" />
                {saving ? '保存' : '添加'}
              </Button>
            </div>

            <p className="hidden text-[10px] leading-4 text-slate-500 lg:block">
              {authenticated ? '已登录：关键词持久化保存到后端数据库。' : '未登录：关键词暂存本地浏览器，登录后可持久化到数据库。'}
            </p>

            <div className="max-h-28 space-y-1 overflow-y-auto pr-0.5 lg:max-h-[260px] lg:space-y-1.5 lg:pr-1">
              {loading ? (
                <div className="rounded-md border border-cyan-300/10 bg-white/[0.03] p-1 text-center text-[7px] text-cyan-100/60 lg:rounded-xl lg:p-3 lg:text-[11px]">
                  正在读取关键词…
                </div>
              ) : visible.length === 0 ? (
                <div className="rounded-md border border-cyan-300/10 bg-white/[0.03] p-1 text-center text-[7px] leading-3 text-slate-500 lg:rounded-xl lg:p-3 lg:text-[11px]">
                  暂无关键词。开始语音对话后，会自动记录关键词。
                </div>
              ) : (
                visible.map((item) => (
                  <KeywordRow
                    key={item.id}
                    item={item}
                    onDelete={() => onDelete(item)}
                  />
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function KeywordRow({ item, onDelete }: { item: VoiceKeyword; onDelete: () => void }) {
  return (
    <div className="group rounded-md border border-cyan-300/10 bg-white/[0.035] p-1 transition hover:border-cyan-300/25 hover:bg-cyan-300/[0.055] lg:rounded-xl lg:p-2.5">
      <div className="flex items-start justify-between gap-1 lg:gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-1 lg:gap-1.5">
            <span className="rounded-full bg-cyan-300/12 px-1 py-0.5 text-[7px] font-semibold text-cyan-50 lg:px-2 lg:text-[12px]">
              {item.keyword}
            </span>
            <span className="rounded-full border border-cyan-300/15 px-1 py-0.5 font-mono text-[6px] text-cyan-100/60 lg:px-1.5 lg:text-[9px]">
              {item.sourceType === 'MANUAL' ? 'MANUAL' : item.sourceType === 'AI' ? 'AI' : 'AUTO'} · {item.hitCount ?? 1}
            </span>
          </div>
          {item.sourceText ? (
            <p className="hidden lg:mt-1 lg:line-clamp-2 lg:text-[10px] lg:leading-4 lg:text-slate-400">
              {item.sourceText}
            </p>
          ) : null}
        </div>
        <button
          type="button"
          onClick={onDelete}
          className="shrink-0 rounded-md p-0.5 text-slate-500 opacity-0 transition hover:bg-rose-400/10 hover:text-rose-200 group-hover:opacity-100 lg:rounded-lg lg:p-1"
          title="删除关键词"
        >
          <Trash2 className="h-2.5 w-2.5 lg:h-3.5 lg:w-3.5" />
        </button>
      </div>
    </div>
  );
}


function Metric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-xl border border-cyan-300/10 bg-white/[0.035] p-2 backdrop-blur-xl lg:rounded-2xl lg:p-3">
      <div className="mb-1 flex items-center gap-1.5 text-cyan-200 lg:mb-2 lg:gap-2">
        {icon}
        <span className="font-mono text-[9px] uppercase tracking-[0.18em] text-cyan-200/70 lg:text-[10px] lg:tracking-[0.24em]">{label}</span>
      </div>
      <div className="truncate text-xs font-medium text-white lg:text-sm">{value}</div>
    </div>
  );
}

function Level({ label, value, active }: { label: string; value: number; active: boolean }) {
  const pct = Math.min(100, Math.max(active ? 8 : 0, value * 120));
  return (
    <div className="mt-1 lg:mt-3">
      <div className="mb-0.5 flex items-center justify-between font-mono text-[6px] tracking-[0.06em] text-slate-500 lg:mb-1 lg:text-[10px] lg:tracking-[0.2em]">
        <span>{label}</span>
        <span>{Math.round(pct)}%</span>
      </div>
      <div className="h-1 overflow-hidden rounded-full bg-white/10 lg:h-1.5">
        <div
          className="h-full rounded-full bg-gradient-to-r from-cyan-300 via-sky-300 to-fuchsia-300 transition-[width] duration-75"
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

function ParticleVoiceSphere({ energy, active, stateLabel }: { energy: number; active: boolean; stateLabel: string }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const energyRef = useRef(energy);
  const activeRef = useRef(active);
  const labelRef = useRef(stateLabel);
  const titleRef = useRef(ORBIT_WORD);
  const subtitleRef = useRef('VOICE CORE');

  useEffect(() => {
    energyRef.current = energy;
  }, [energy]);

  useEffect(() => {
    activeRef.current = active;
  }, [active]);

  useEffect(() => {
    labelRef.current = stateLabel;
  }, [stateLabel]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;

    let raf = 0;
    let width = 1;
    let height = 1;
    let dpr = 1;
    let lastFrameAt = performance.now();
    const start = lastFrameAt;
    let smoothedEnergy = 0;
    let beatEnergy = 0;
    const dots: Array<{ x: number; y: number; z: number; s: number; a: number }> = [];

    const resize = () => {
      const rect = canvas.getBoundingClientRect();
      dpr = Math.min(2.5, Math.max(1, window.devicePixelRatio || 1));
      width = Math.max(1, rect.width);
      height = Math.max(1, rect.height);
      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
    };

    const drawOrbitText = (cx: number, cy: number, orbit: number, front: boolean, time: number, speed: number, pulse: number) => {
      const letters = titleRef.current.split('');
      for (const band of TEXT_BANDS) {
        const rx = orbit * band.radius;
        const ry = orbit * band.yScale;
        const rot = band.tilt;
        const cosR = Math.cos(rot);
        const sinR = Math.sin(rot);
        const phaseOffset = time * speed * band.speed + band.phase;
        const startAngle = front ? 0 : Math.PI;
        const endAngle = front ? Math.PI : Math.PI * 2;

        ctx.save();
        ctx.lineCap = 'round';
        ctx.globalAlpha = (front ? 0.11 : 0.05) * band.alpha + pulse * (front ? 0.06 : 0.03);
        ctx.strokeStyle = front ? 'rgba(103,232,249,0.46)' : 'rgba(59,130,246,0.22)';
        ctx.lineWidth = (front ? 5.2 : 3.6) + pulse * 1.4;
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, rot, startAngle, endAngle);
        ctx.stroke();

        ctx.globalAlpha = (front ? 0.32 : 0.12) * band.alpha + pulse * (front ? 0.08 : 0.04);
        ctx.strokeStyle = front ? 'rgba(240,253,250,0.80)' : 'rgba(167,243,208,0.34)';
        ctx.lineWidth = 0.8 + pulse * 0.35;
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, rot, startAngle, endAngle);
        ctx.stroke();
        ctx.restore();

        const letterCount = letters.length;
        for (let i = 0; i < band.count; i += 1) {
          const phase = phaseOffset + (i * Math.PI * 2) / band.count;
          const sinP = Math.sin(phase);
          const z = (sinP + 1) / 2;
          const isFront = z >= 0.47;
          if (isFront !== front) continue;

          const rawX = Math.cos(phase) * rx;
          const rawY = sinP * ry;
          const x = cx + rawX * cosR - rawY * sinR;
          const y = cy + rawX * sinR + rawY * cosR - z * 14;
          const dx = -Math.sin(phase) * rx;
          const dy = Math.cos(phase) * ry;
          const tx = dx * cosR - dy * sinR;
          const ty = dx * sinR + dy * cosR;
          let angle = Math.atan2(ty, tx);
          if (angle > Math.PI / 2 || angle < -Math.PI / 2) angle += Math.PI;

          const depthScale = 0.78 + z * 0.42;
          const fontSize = Math.max(10, (11.2 + pulse * 2.6) * band.font * depthScale);
          const alpha = Math.min(1, ((front ? 0.24 + z * 0.34 : 0.08 + z * 0.14) + pulse * 0.1) * band.alpha);
          const letter = letters[i % letterCount];

          ctx.save();
          ctx.translate(x, y);
          ctx.rotate(angle);
          ctx.globalAlpha = alpha;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.font = `700 ${fontSize}px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace`;
          ctx.shadowColor = front ? 'rgba(103,232,249,0.8)' : 'rgba(59,130,246,0.38)';
          ctx.shadowBlur = front ? 8 + pulse * 10 : 2;
          ctx.lineWidth = 2;
          ctx.strokeStyle = 'rgba(2,6,23,0.55)';
          ctx.strokeText(letter, 0, 0.5);
          ctx.fillStyle = front ? 'rgba(236,254,255,0.95)' : 'rgba(125,211,252,0.56)';
          ctx.fillText(letter, 0, 0.5);
          ctx.restore();
        }
      }
    };

    const drawSoundWaveRings = (cx: number, cy: number, radius: number, time: number, pulse: number, activeBoost: number) => {
      ctx.save();
      ctx.lineCap = 'round';
      ctx.globalCompositeOperation = 'lighter';
      for (let i = 0; i < 3; i += 1) {
        const phase = (time * (0.22 + activeBoost * 0.18) + i * 0.3) % 1;
        const waveRadius = radius * (0.94 + phase * 0.08);
        const alpha = (1 - phase) * (0.07 + activeBoost * 0.12 + pulse * 0.04);
        const wave = ctx.createLinearGradient(cx - waveRadius, cy, cx + waveRadius, cy);
        wave.addColorStop(0, 'rgba(56,189,248,0)');
        wave.addColorStop(0.18, `rgba(56,189,248,${alpha * 0.9})`);
        wave.addColorStop(0.5, `rgba(236,254,255,${alpha * 1.6})`);
        wave.addColorStop(0.82, `rgba(45,212,191,${alpha})`);
        wave.addColorStop(1, 'rgba(56,189,248,0)');
        ctx.globalAlpha = Math.max(0.02, alpha);
        ctx.strokeStyle = wave;
        ctx.lineWidth = Math.max(1, radius * (0.006 + phase * 0.004));
        ctx.shadowColor = 'rgba(34,211,238,0.65)';
        ctx.shadowBlur = 9 + pulse * 7;
        ctx.beginPath();
        ctx.ellipse(cx, cy, waveRadius, waveRadius * (0.47 + phase * 0.018), -0.08, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();
    };

    const drawFlowTrails = (cx: number, cy: number, orbit: number, time: number, pulse: number, activeBoost: number) => {
      const trails = [
        { radius: 0.98, yScale: 0.35, tilt: -0.24, speed: 1.18, phase: 0, sweep: 0.58 },
        { radius: 0.9, yScale: 0.27, tilt: 0.34, speed: -1.02, phase: 1.6, sweep: 0.46 },
        { radius: 0.78, yScale: 0.2, tilt: -0.7, speed: 1.45, phase: 2.4, sweep: 0.36 },
      ];

      ctx.save();
      ctx.lineCap = 'round';
      ctx.globalCompositeOperation = 'lighter';
      for (const trail of trails) {
        const rx = orbit * trail.radius;
        const ry = orbit * trail.yScale;
        const start = time * trail.speed + trail.phase;
        const end = start + trail.sweep + pulse * 0.18;
        const glow = ctx.createLinearGradient(cx - rx, cy - ry, cx + rx, cy + ry);
        glow.addColorStop(0, 'rgba(56,189,248,0)');
        glow.addColorStop(0.42, `rgba(56,189,248,${0.12 + activeBoost * 0.18})`);
        glow.addColorStop(0.66, `rgba(236,254,255,${0.28 + pulse * 0.18})`);
        glow.addColorStop(1, `rgba(45,212,191,${0.1 + activeBoost * 0.14})`);
        ctx.globalAlpha = 0.58 + pulse * 0.16;
        ctx.strokeStyle = glow;
        ctx.lineWidth = Math.max(1.4, orbit * 0.006);
        ctx.shadowColor = 'rgba(34,211,238,0.8)';
        ctx.shadowBlur = 14 + pulse * 14;
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, trail.tilt, start, end);
        ctx.stroke();

        const tipPhase = end;
        const rawX = Math.cos(tipPhase) * rx;
        const rawY = Math.sin(tipPhase) * ry;
        const cosR = Math.cos(trail.tilt);
        const sinR = Math.sin(trail.tilt);
        const tipX = cx + rawX * cosR - rawY * sinR;
        const tipY = cy + rawX * sinR + rawY * cosR;
        ctx.globalAlpha = 0.62 + pulse * 0.22;
        ctx.fillStyle = 'rgba(240,253,250,0.96)';
        ctx.shadowBlur = 18 + pulse * 16;
        ctx.beginPath();
        ctx.arc(tipX, tipY, Math.max(2, orbit * 0.008), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    };

    const drawScanLines = (cx: number, cy: number, radius: number, time: number, pulse: number, activeBoost: number) => {
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.02, 0, Math.PI * 2);
      ctx.clip();
      ctx.lineCap = 'round';
      ctx.globalCompositeOperation = 'lighter';

      for (let i = -6; i <= 6; i += 1) {
        const y = (i / 6) * radius * 0.68;
        const ratio = Math.abs(y) / (radius * 0.72);
        const rx = radius * Math.sqrt(Math.max(0.08, 1 - ratio * ratio));
        ctx.globalAlpha = 0.06 + pulse * 0.025;
        ctx.strokeStyle = 'rgba(125,211,252,0.58)';
        ctx.lineWidth = 0.7;
        ctx.beginPath();
        ctx.ellipse(cx, cy + y, rx, radius * 0.022, 0, 0, Math.PI * 2);
        ctx.stroke();
      }

      const scanY = Math.sin(time * (1.6 + activeBoost * 0.8)) * radius * 0.58;
      const scanRatio = Math.abs(scanY) / (radius * 0.72);
      const scanRx = radius * Math.sqrt(Math.max(0.08, 1 - scanRatio * scanRatio));
      const scan = ctx.createLinearGradient(cx - scanRx, cy + scanY, cx + scanRx, cy + scanY);
      scan.addColorStop(0, 'rgba(56,189,248,0)');
      scan.addColorStop(0.2, `rgba(34,211,238,${0.16 + activeBoost * 0.18})`);
      scan.addColorStop(0.5, `rgba(240,253,250,${0.48 + pulse * 0.22})`);
      scan.addColorStop(0.8, `rgba(45,212,191,${0.16 + activeBoost * 0.16})`);
      scan.addColorStop(1, 'rgba(56,189,248,0)');
      ctx.globalAlpha = 0.76;
      ctx.strokeStyle = scan;
      ctx.lineWidth = Math.max(1.2, radius * 0.012);
      ctx.shadowColor = 'rgba(103,232,249,0.85)';
      ctx.shadowBlur = 16 + pulse * 14;
      ctx.beginPath();
      ctx.ellipse(cx, cy + scanY, scanRx, radius * 0.034, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    };

    const drawCore = (cx: number, cy: number, radius: number, time: number, pulse: number, activeBoost: number) => {
      const ringOuter = radius * (1.04 + pulse * 0.025);
      const ringInner = radius * 0.74;

      ctx.save();
      const glass = ctx.createRadialGradient(cx - radius * 0.22, cy - radius * 0.28, 0, cx, cy, radius * 1.15);
      glass.addColorStop(0, `rgba(255,255,255,${0.18 + pulse * 0.05})`);
      glass.addColorStop(0.35, `rgba(125,211,252,${0.08 + pulse * 0.06})`);
      glass.addColorStop(1, 'rgba(2,6,23,0)');
      ctx.fillStyle = glass;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.08, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.65;
      ctx.strokeStyle = 'rgba(103,232,249,0.18)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.arc(cx, cy, ringOuter, 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 0.42;
      ctx.beginPath();
      ctx.arc(cx, cy, ringInner, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      ctx.save();
      ctx.lineCap = 'round';
      ctx.globalAlpha = 0.34 + activeBoost * 0.2;
      ctx.strokeStyle = 'rgba(103,232,249,0.42)';
      ctx.lineWidth = Math.max(1, radius * 0.006);
      const segmentCount = 18;
      const spin = time * (0.22 + activeBoost * 0.15);
      for (let i = 0; i < segmentCount; i += 1) {
        const a = spin + (i * Math.PI * 2) / segmentCount;
        const len = 0.065 + (i % 3) * 0.014;
        ctx.beginPath();
        ctx.arc(cx, cy, ringOuter * (1.005 + (i % 2) * 0.014), a, a + len);
        ctx.stroke();
      }
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.18 + pulse * 0.08;
      ctx.strokeStyle = 'rgba(255,255,255,0.8)';
      ctx.lineWidth = 0.9;
      for (let i = 0; i < 3; i += 1) {
        const rr = radius * (0.84 + i * 0.12);
        ctx.beginPath();
        ctx.ellipse(cx, cy, rr, rr * (0.33 + i * 0.04), -0.35, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.14 + activeBoost * 0.1;
      ctx.strokeStyle = 'rgba(167,243,208,0.28)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(cx - radius * 0.92, cy + radius * 0.52);
      ctx.lineTo(cx + radius * 0.92, cy - radius * 0.52);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(cx - radius * 0.96, cy - radius * 0.34);
      ctx.lineTo(cx + radius * 0.96, cy + radius * 0.34);
      ctx.stroke();
      ctx.restore();

      ctx.save();
      const flare = ctx.createLinearGradient(cx - radius * 0.72, cy - radius * 0.56, cx + radius * 0.5, cy + radius * 0.36);
      flare.addColorStop(0, 'rgba(255,255,255,0)');
      flare.addColorStop(0.36, `rgba(240,253,250,${0.08 + pulse * 0.08})`);
      flare.addColorStop(0.52, `rgba(125,211,252,${0.16 + pulse * 0.1})`);
      flare.addColorStop(0.68, 'rgba(255,255,255,0)');
      ctx.globalAlpha = 0.68;
      ctx.strokeStyle = flare;
      ctx.lineWidth = Math.max(1, radius * 0.018);
      ctx.beginPath();
      ctx.ellipse(cx - radius * 0.08, cy - radius * 0.1, radius * 0.82, radius * 0.18, -0.52, Math.PI * 0.08, Math.PI * 0.86);
      ctx.stroke();
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.9;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = `600 ${Math.max(9, radius * 0.07)}px ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`;
      ctx.shadowColor = 'rgba(34,211,238,0.38)';
      ctx.shadowBlur = 10 + pulse * 8;
      ctx.fillStyle = 'rgba(186,230,253,0.70)';
      ctx.fillText(subtitleRef.current, cx, cy - radius * 0.18);
      ctx.restore();

      ctx.save();
      ctx.translate(cx, cy + radius * 0.02);
      ctx.scale(1.16, 0.74);
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const titleFont = Math.max(24, radius * 0.255);
      ctx.font = `900 ${titleFont}px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace`;
      ctx.globalAlpha = 0.23;
      ctx.shadowColor = 'rgba(34,211,238,0.72)';
      ctx.shadowBlur = 22 + pulse * 18;
      ctx.fillStyle = 'rgba(14,165,233,0.55)';
      ctx.fillText(titleRef.current, 0, radius * 0.12);
      ctx.globalAlpha = 0.36;
      ctx.shadowBlur = 16 + pulse * 12;
      ctx.strokeStyle = 'rgba(2,6,23,0.70)';
      ctx.lineWidth = Math.max(2.2, radius * 0.026);
      ctx.strokeText(titleRef.current, 0, 0);
      const depthGradient = ctx.createLinearGradient(-radius * 0.7, -radius * 0.2, radius * 0.72, radius * 0.18);
      depthGradient.addColorStop(0, 'rgba(56,189,248,0.92)');
      depthGradient.addColorStop(0.36, 'rgba(236,254,255,0.98)');
      depthGradient.addColorStop(0.68, 'rgba(125,211,252,0.92)');
      depthGradient.addColorStop(1, 'rgba(167,243,208,0.88)');
      ctx.globalAlpha = 0.88;
      ctx.shadowColor = 'rgba(34,211,238,0.52)';
      ctx.shadowBlur = 10 + pulse * 10;
      ctx.fillStyle = depthGradient;
      ctx.fillText(titleRef.current, 0, 0);
      ctx.globalAlpha = 0.42;
      ctx.shadowBlur = 0;
      ctx.strokeStyle = 'rgba(240,253,250,0.62)';
      ctx.lineWidth = Math.max(0.8, radius * 0.008);
      ctx.strokeText(titleRef.current, 0, -radius * 0.012);
      ctx.restore();

      ctx.save();
      const statusText = labelRef.current;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = `700 ${Math.max(10, radius * 0.062)}px ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`;
      const badgeWidth = Math.max(radius * 0.52, ctx.measureText(statusText).width + radius * 0.22);
      const badgeHeight = Math.max(18, radius * 0.14);
      const badgeX = cx - badgeWidth / 2;
      const badgeY = cy + radius * 0.33;
      const badgeRadius = badgeHeight / 2;
      const badgeGradient = ctx.createLinearGradient(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight);
      badgeGradient.addColorStop(0, `rgba(8,47,73,${0.66 + activeBoost * 0.12})`);
      badgeGradient.addColorStop(0.5, `rgba(14,116,144,${0.58 + activeBoost * 0.16})`);
      badgeGradient.addColorStop(1, `rgba(15,118,110,${0.58 + activeBoost * 0.12})`);
      ctx.fillStyle = badgeGradient;
      ctx.strokeStyle = `rgba(186,230,253,${0.3 + activeBoost * 0.2})`;
      ctx.lineWidth = 1;
      ctx.shadowColor = 'rgba(34,211,238,0.35)';
      ctx.shadowBlur = 14 + pulse * 8;
      ctx.beginPath();
      ctx.roundRect(badgeX, badgeY, badgeWidth, badgeHeight, badgeRadius);
      ctx.fill();
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.fillStyle = 'rgba(240,253,250,0.92)';
      ctx.fillText(statusText, cx, badgeY + badgeHeight / 2 + 0.5);
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.16 + activeBoost * 0.08;
      ctx.strokeStyle = 'rgba(255,255,255,0.4)';
      ctx.lineWidth = 0.8;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.02, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      if (activeBoost > 0.15) {
        ctx.save();
        ctx.globalAlpha = 0.28 + pulse * 0.1;
        ctx.strokeStyle = 'rgba(103,232,249,0.36)';
        ctx.lineWidth = 1.1;
        for (let i = 0; i < 3; i += 1) {
          const offset = radius * (0.94 + i * 0.08);
          ctx.beginPath();
          ctx.ellipse(cx, cy, offset, offset * (0.12 + i * 0.05), time * 0.2 + i * 0.5, 0, Math.PI * 2);
          ctx.stroke();
        }
        ctx.restore();
      }

      const highlight = ctx.createRadialGradient(cx - radius * 0.25, cy - radius * 0.35, 0, cx, cy, radius * 0.95);
      highlight.addColorStop(0, `rgba(255,255,255,${0.24 + pulse * 0.06})`);
      highlight.addColorStop(0.35, `rgba(125,211,252,${0.07 + pulse * 0.05})`);
      highlight.addColorStop(1, 'rgba(2,6,23,0)');
      ctx.fillStyle = highlight;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 0.96, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    };

    const render = (now: number) => {
      const t = (now - start) / 1000;
      const dt = Math.min(0.05, Math.max(0.001, (now - lastFrameAt) / 1000));
      lastFrameAt = now;
      const targetEnergy = Math.min(1, Math.max(0, energyRef.current));
      smoothedEnergy += (targetEnergy - smoothedEnergy) * (1 - Math.exp(-(targetEnergy > smoothedEnergy ? 14 : 7) * dt));
      beatEnergy += (targetEnergy - beatEnergy) * (1 - Math.exp(-(targetEnergy > beatEnergy ? 24 : 9) * dt));
      const e = Math.max(smoothedEnergy, beatEnergy * 0.74);
      const pulse = Math.min(1, 0.12 + e * 2.15);
      const cx = width / 2;
      const cy = height / 2;
      const min = Math.min(width, height);
      const voiceScale = 1 + e * 0.065 + Math.sin(t * 14) * e * 0.008;
      const radius = min * (0.238 + pulse * 0.018) * voiceScale;
      const sphereRadius = radius * 1.075;
      const orbit = min * (0.302 + pulse * 0.008) * voiceScale;
      const speed = 0.34 + pulse * 0.28 + (activeRef.current ? 0.1 : 0);
      const activeBoost = activeRef.current ? 0.38 + e * 0.4 : 0.12 + e * 0.15;
      const ambientPulse = 0.5 + Math.sin(t * 1.15) * 0.5;

      ctx.clearRect(0, 0, width, height);

      const bg = ctx.createRadialGradient(cx, cy, 0, cx, cy, min * 0.52);
      bg.addColorStop(0, `rgba(240,253,250,${0.08 + pulse * 0.08})`);
      bg.addColorStop(0.28, `rgba(45,212,191,${0.13 + pulse * 0.11})`);
      bg.addColorStop(0.58, `rgba(56,189,248,${0.09 + pulse * 0.08})`);
      bg.addColorStop(0.82, `rgba(99,102,241,${0.035 + ambientPulse * 0.035})`);
      bg.addColorStop(1, 'rgba(2,6,23,0)');
      ctx.fillStyle = bg;
      ctx.beginPath();
      ctx.arc(cx, cy, min * 0.54, 0, Math.PI * 2);
      ctx.fill();

      const shadow = ctx.createRadialGradient(cx, cy + radius * 1.46, 0, cx, cy + radius * 1.46, radius * 1.7);
      shadow.addColorStop(0, 'rgba(0,0,0,0.52)');
      shadow.addColorStop(0.45, 'rgba(15,23,42,0.22)');
      shadow.addColorStop(1, 'rgba(15,23,42,0)');
      ctx.save();
      ctx.scale(1, 0.16);
      ctx.fillStyle = shadow;
      ctx.beginPath();
      ctx.arc(cx, (cy + radius * 1.62) / 0.16, radius * 1.36, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();

      const backHalo = ctx.createRadialGradient(cx, cy - sphereRadius * 0.08, sphereRadius * 0.42, cx, cy, sphereRadius * 1.2);
      backHalo.addColorStop(0, 'rgba(255,255,255,0)');
      backHalo.addColorStop(0.5, `rgba(34,211,238,${0.08 + pulse * 0.06})`);
      backHalo.addColorStop(0.72, `rgba(14,165,233,${0.09 + ambientPulse * 0.04})`);
      backHalo.addColorStop(1, 'rgba(14,165,233,0)');
      ctx.fillStyle = backHalo;
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 1.12, 0, Math.PI * 2);
      ctx.fill();

      drawSoundWaveRings(cx, cy, sphereRadius, t, pulse, activeBoost);
      drawOrbitText(cx, cy, orbit, false, t, speed, pulse);
      drawFlowTrails(cx, cy, sphereRadius, t, pulse, activeBoost);

      const rotY = t * (0.42 + pulse * 0.22);
      const rotX = -0.44 + Math.sin(t * 0.38) * 0.07;
      const sinY = Math.sin(rotY);
      const cosY = Math.cos(rotY);
      const sinX = Math.sin(rotX);
      const cosX = Math.cos(rotX);
      dots.length = 0;

      for (const p of SPHERE_PARTICLES) {
        const theta = p.theta + t * (0.08 + e * 0.05) * p.speed;
        const x = Math.sin(p.phi) * Math.cos(theta);
        const y = Math.cos(p.phi);
        const z = Math.sin(p.phi) * Math.sin(theta);
        const x1 = x * cosY - z * sinY;
        const z1 = x * sinY + z * cosY;
        const y1 = y * cosX - z1 * sinX;
        const z2 = y * sinX + z1 * cosX;
        const perspective = 0.8 + z2 * 0.22;
        dots.push({
          x: cx + x1 * sphereRadius * perspective,
          y: cy + y1 * sphereRadius * perspective,
          z: z2,
          s: Math.max(0.5, (p.size + pulse * 1.18 + e * 0.82) * (0.78 + z2 * 0.28)),
          a: 0.1 + (z2 + 1) * 0.26 + pulse * 0.16 + Math.sin(t * (2 + e * 1.6) + p.twinkle * 8) * 0.04,
        });
      }
      dots.sort((a, b) => a.z - b.z);
      for (const p of dots) {
        const frontDepth = Math.max(0, Math.min(1, (p.z + 1) / 2));
        ctx.globalAlpha = Math.max(0.05, Math.min(1, p.a * (0.64 + frontDepth * 0.52)));
        ctx.fillStyle = p.z > 0.28 ? 'rgba(255,255,255,0.96)' : p.z > -0.2 ? 'rgba(125,211,252,0.72)' : 'rgba(59,130,246,0.36)';
        ctx.shadowBlur = p.z > 0.42 ? 5 + pulse * 4 : p.z > 0.05 ? 1.5 : 0;
        ctx.shadowColor = p.z > 0.28 ? 'rgba(240,253,250,0.78)' : 'rgba(103,232,249,0.62)';
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.s, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.shadowBlur = 0;

      const shell = ctx.createRadialGradient(cx - sphereRadius * 0.32, cy - sphereRadius * 0.4, 0, cx, cy, sphereRadius * 1.08);
      shell.addColorStop(0, 'rgba(255,255,255,0.16)');
      shell.addColorStop(0.3, 'rgba(153,246,228,0.11)');
      shell.addColorStop(0.72, 'rgba(14,116,144,0.06)');
      shell.addColorStop(1, 'rgba(2,6,23,0.02)');
      ctx.fillStyle = shell;
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 1.03, 0, Math.PI * 2);
      ctx.fill();

      ctx.save();
      ctx.strokeStyle = `rgba(255,255,255,${0.22 + pulse * 0.22})`;
      ctx.lineWidth = 1.15;
      ctx.shadowColor = 'rgba(103,232,249,0.45)';
      ctx.shadowBlur = 20 + pulse * 14;
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 1.04, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();

      ctx.save();
      const rim = ctx.createLinearGradient(cx - sphereRadius, cy - sphereRadius, cx + sphereRadius, cy + sphereRadius);
      rim.addColorStop(0, 'rgba(255,255,255,0)');
      rim.addColorStop(0.28, `rgba(236,254,255,${0.12 + pulse * 0.08})`);
      rim.addColorStop(0.52, 'rgba(255,255,255,0)');
      rim.addColorStop(0.78, `rgba(103,232,249,${0.10 + pulse * 0.06})`);
      rim.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.globalAlpha = 0.86;
      ctx.strokeStyle = rim;
      ctx.lineWidth = Math.max(1, sphereRadius * 0.014);
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 1.075, Math.PI * 1.06, Math.PI * 1.88);
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 1.075, Math.PI * 0.02, Math.PI * 0.34);
      ctx.stroke();
      ctx.restore();

      ctx.save();
      ctx.globalAlpha = 0.12 + pulse * 0.08;
      ctx.strokeStyle = 'rgba(167,243,208,0.5)';
      ctx.lineWidth = 0.75;
      for (let i = -2; i <= 2; i += 1) {
        const k = i / 3;
        ctx.beginPath();
        ctx.ellipse(cx, cy + k * sphereRadius * 0.68, sphereRadius * Math.sqrt(Math.max(0.1, 1 - k * k)), sphereRadius * 0.16, 0, 0, Math.PI * 2);
        ctx.stroke();
      }
      for (let i = 0; i < 3; i += 1) {
        ctx.beginPath();
        ctx.ellipse(cx, cy, sphereRadius * (0.26 + i * 0.16), sphereRadius * (0.92 + i * 0.04), Math.PI / 2 + i * 0.18 + rotY * 0.12, 0, Math.PI * 2);
        ctx.stroke();
      }
      ctx.restore();

      drawScanLines(cx, cy, sphereRadius, t, pulse, activeBoost);

      const core = ctx.createRadialGradient(cx, cy, 0, cx, cy, sphereRadius * 0.62);
      core.addColorStop(0, `rgba(240,253,250,${0.34 + pulse * 0.22})`);
      core.addColorStop(0.35, `rgba(45,212,191,${0.22 + pulse * 0.15})`);
      core.addColorStop(1, 'rgba(45,212,191,0)');
      ctx.fillStyle = core;
      ctx.beginPath();
      ctx.arc(cx, cy, sphereRadius * 0.58, 0, Math.PI * 2);
      ctx.fill();

      drawCore(cx, cy, sphereRadius, t, pulse, activeBoost);
      drawOrbitText(cx, cy, orbit, true, t, speed, pulse);

      raf = window.requestAnimationFrame(render);
    };

    const ro = new ResizeObserver(resize);
    ro.observe(canvas);
    resize();
    raf = window.requestAnimationFrame(render);
    return () => {
      window.cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, []);

  return <canvas ref={canvasRef} className="h-full w-full rounded-full" aria-hidden />;
}
