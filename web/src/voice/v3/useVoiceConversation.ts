import { useCallback, useEffect, useRef, useState } from 'react';

import { useAuthStore } from '@/stores/auth';
import { audioMime, isPcmFormat, sampleRateFromFormat, startPcmCapture, type CaptureHandle } from './audio';
import {
  buildVoiceV3WebSocketUrl,
  getVoiceAsrDisplayText,
  type VoiceClientMessageV3,
  type VoiceModeV3,
  type VoiceServerMessageV3,
  type VoiceStateV3,
  type VoiceWsStatusV3,
} from './protocol';

export interface VoiceConversationOptions {
  kbIds?: (string | number)[];
  personaId?: string;
  globalKb?: boolean;
  publicAccess?: boolean;
  mode?: VoiceModeV3;
  autoRestart?: boolean;
  silenceMs?: number;
  minRecordingMs?: number;
  maxRecordingMs?: number;
  speechRms?: number;
  speechPeak?: number;
  inputDeviceId?: string;
  onServer?: (msg: VoiceServerMessageV3) => void;
  onError?: (err: Error) => void;
  onAutoEnd?: (reason: 'silence' | 'maxLength') => void;
}

export interface VoiceConversationHandle {
  status: VoiceWsStatusV3;
  voiceState: VoiceStateV3;
  recording: boolean;
  inCall: boolean;
  error?: string;
  sessionId?: string;
  volume: number;
  playbackVolume: number;
  connect: () => Promise<void>;
  disconnect: () => void;
  startTurn: (sessionId?: string) => Promise<void>;
  endTurn: () => void;
  startCall: (sessionId?: string) => Promise<void>;
  endCall: () => void;
  interrupt: () => void;
  resetConversation: () => void;
  setSessionId: (sessionId?: string) => void;
  /**
   * TTS 播放时钟（用于实现卡拉 OK 风格字幕，不会触发 React 重渲染）：
   * - currentTime：AudioContext 当前已播放到的秒数
   * - queuedTime：所有已加载到 AudioContext 的 PCM 帧累计排队时间（>= currentTime）
   * - 还未启动 / 已结束播放时返回 null
   */
  getPlaybackClock: () => { currentTime: number; queuedTime: number } | null;
}

const DEFAULTS = {
  mode: 'HANDS_FREE' as VoiceModeV3,
  autoRestart: true,
  silenceMs: 900,
  minRecordingMs: 700,
  maxRecordingMs: 60000,
  speechRms: 0.003,
  speechPeak: 0.012,
};

function sttLog(stage: string, data?: Record<string, unknown>) {
  if (data) console.info(`[Voice-V3][STT] ${stage}`, data);
  else console.info(`[Voice-V3][STT] ${stage}`);
}

function redactVoiceWsUrl(raw: string) {
  try {
    const url = new URL(raw);
    if (url.searchParams.has('token')) url.searchParams.set('token', '***');
    return url.toString();
  } catch {
    return raw.replace(/([?&]token=)[^&]+/i, '$1***');
  }
}

export function useVoiceConversation(options: VoiceConversationOptions = {}): VoiceConversationHandle {
  const token = useAuthStore((s) => s.accessToken);
  const optsRef = useRef(options);
  optsRef.current = options;

  const [status, setStatus] = useState<VoiceWsStatusV3>('idle');
  const [voiceState, _setVoiceState] = useState<VoiceStateV3>('IDLE');
  // setVoiceState 包一层：每次更新时同步把镜像 ref 也写最新值，
  // 这样依赖闭包外读 ref 的回调（如麦克风 frame 回调）总能拿到最新状态。
  const setVoiceState = useCallback((next: VoiceStateV3) => {
    voiceStateRef.current = next;
    _setVoiceState(next);
  }, []);
  const [recording, setRecording] = useState(false);
  const [inCall, setInCall] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const [sessionId, setSessionIdState] = useState<string | undefined>();
  const [volume, setVolume] = useState(0);
  const [playbackVolume, setPlaybackVolume] = useState(0);

  const wsRef = useRef<WebSocket | null>(null);
  const connectPromiseRef = useRef<Promise<void> | null>(null);
  const captureRef = useRef<CaptureHandle | null>(null);
  const recordingRef = useRef(false);
  const inCallRef = useRef(false);
  const sessionIdRef = useRef<string | undefined>();
  const prevStateRef = useRef<VoiceStateV3>('IDLE');
  // 实时状态镜像，供 capture 回调（在 audio worklet 线程定期触发）读取最新值，
  // 避免 React 闭包里的 voiceState 滞后导致"AI 还在说就开始采麦"。
  const voiceStateRef = useRef<VoiceStateV3>('IDLE');

  const recordingStartedAtRef = useRef(0);
  const lastSpeechAtRef = useRef(0);
  const hasSpeechRef = useRef(false);
  const speechFramesRef = useRef(0);
  const autoEndingRef = useRef(false);
  const maxTimerRef = useRef<number | undefined>();
  const noiseRmsRef = useRef(0.001);
  const noisePeakRef = useRef(0.003);
  const maxSpeechRmsRef = useRef(0);
  const maxSpeechPeakRef = useRef(0);
  const lastVolumeUpdateAtRef = useRef(0);
  const lastVadDiagnoseAtRef = useRef(0);
  // 最近 ~1.2s 的能量历史，用于"滑动窗口最低水位线"兜底判定停顿
  const energyHistoryRef = useRef<{ t: number; rms: number; peak: number }[]>([]);
  const lastQuietAtRef = useRef(0);
  // UI MIC 显示电平 ≥ 50% 的最近时刻；用于"连续 1.5s 未达到 50%"兑底触发。
  const lastLoudUiAtRef = useRef(0);
  const hadLoudUiInputRef = useRef(false);

  const playCtxRef = useRef<AudioContext | null>(null);
  const playTimeRef = useRef(0);
  const playbackGenerationRef = useRef(0);
  const ttsFormatRef = useRef('pcm-16000hz-mono-16bit');
  const encodedChunksRef = useRef<ArrayBuffer[]>([]);
  const encodedQueueRef = useRef(Promise.resolve());
  const playingElementsRef = useRef<Set<HTMLAudioElement>>(new Set());
  const playAnalyserRef = useRef<AnalyserNode | null>(null);
  const playLevelRafRef = useRef<number | null>(null);
  const playbackVolumeRef = useRef(0);
  const emittedPlaybackVolumeRef = useRef(0);
  const lastPlaybackVolumeEmitAtRef = useRef(0);

  const send = useCallback((msg: VoiceClientMessageV3) => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      sttLog('send.control.drop', { type: msg.type, wsState: ws?.readyState ?? 'none' });
      return;
    }
    sttLog('send.control', {
      type: msg.type,
      mode: msg.mode,
      sessionId: msg.sessionId,
      kbCount: msg.kbIds?.length ?? 0,
      globalKb: msg.globalKb,
      sampleRate: msg.sampleRate,
      inputAudioFormat: msg.inputAudioFormat,
    });
    ws.send(JSON.stringify(msg));
  }, []);

  const clearTimers = useCallback(() => {
    if (maxTimerRef.current != null) window.clearTimeout(maxTimerRef.current);
    maxTimerRef.current = undefined;
  }, []);

  const cleanupCapture = useCallback(() => {
    const hadCapture = Boolean(captureRef.current);
    const wasRecording = recordingRef.current;
    clearTimers();
    captureRef.current?.stop();
    captureRef.current = null;
    recordingRef.current = false;
    autoEndingRef.current = false;
    setRecording(false);
    setVolume(0);
    if (hadCapture || wasRecording) {
      sttLog('capture.cleanup', { wasRecording, hadCapture });
    }
  }, [clearTimers]);

  const ensurePlayContext = useCallback(() => {
    if (!playCtxRef.current) {
      const Ctor = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext;
      playCtxRef.current = new Ctor();
      playTimeRef.current = playCtxRef.current.currentTime;
      playAnalyserRef.current = null;
    }
    const ctx = playCtxRef.current;
    if (ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
    return ctx;
  }, []);

  const ensurePlayAnalyser = useCallback((ctx: AudioContext) => {
    if (!playAnalyserRef.current) {
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.84;
      analyser.connect(ctx.destination);
      playAnalyserRef.current = analyser;
    }
    if (playLevelRafRef.current == null) {
      const data = new Uint8Array(playAnalyserRef.current.frequencyBinCount);
      const tick = () => {
        const analyser = playAnalyserRef.current;
        if (!analyser || !playCtxRef.current) {
          setPlaybackVolume(0);
          playLevelRafRef.current = null;
          return;
        }
        analyser.getByteTimeDomainData(data);
        let sumSq = 0;
        for (let i = 0; i < data.length; i += 1) {
          const v = (data[i] - 128) / 128;
          sumSq += v * v;
        }
        const target = Math.min(1, Math.sqrt(sumSq / data.length) * 5.2);
        const previous = playbackVolumeRef.current;
        const smoothed = previous + (target - previous) * (target > previous ? 0.38 : 0.14);
        playbackVolumeRef.current = smoothed;
        const now = performance.now();
        if (now - lastPlaybackVolumeEmitAtRef.current >= 50 || Math.abs(smoothed - emittedPlaybackVolumeRef.current) >= 0.16) {
          lastPlaybackVolumeEmitAtRef.current = now;
          emittedPlaybackVolumeRef.current = smoothed;
          setPlaybackVolume(smoothed);
        }
        playLevelRafRef.current = window.requestAnimationFrame(tick);
      };
      playLevelRafRef.current = window.requestAnimationFrame(tick);
    }
    return playAnalyserRef.current;
  }, []);

  const stopPlayback = useCallback(() => {
    playbackGenerationRef.current += 1;
    encodedQueueRef.current = Promise.resolve();
    encodedChunksRef.current = [];
    playingElementsRef.current.forEach((audio) => {
      audio.pause();
      if (audio.src.startsWith('blob:')) URL.revokeObjectURL(audio.src);
    });
    playingElementsRef.current.clear();
    playTimeRef.current = 0;
    playbackVolumeRef.current = 0;
    emittedPlaybackVolumeRef.current = 0;
    lastPlaybackVolumeEmitAtRef.current = 0;
    setPlaybackVolume(0);
    if (playLevelRafRef.current != null) window.cancelAnimationFrame(playLevelRafRef.current);
    playLevelRafRef.current = null;
    playAnalyserRef.current = null;
    if (playCtxRef.current) {
      void playCtxRef.current.close().catch(() => undefined);
      playCtxRef.current = null;
    }
  }, []);

  const playPcm = useCallback((buf: ArrayBuffer, format?: string) => {
    const ctx = ensurePlayContext();
    const view = new DataView(buf);
    const sampleCount = Math.floor(buf.byteLength / 2);
    if (!sampleCount) return;
    const audioBuffer = ctx.createBuffer(1, sampleCount, sampleRateFromFormat(format));
    const channel = audioBuffer.getChannelData(0);
    for (let i = 0; i < sampleCount; i += 1) channel[i] = view.getInt16(i * 2, true) / 0x8000;
    const src = ctx.createBufferSource();
    src.buffer = audioBuffer;
    src.connect(ensurePlayAnalyser(ctx));
    const now = ctx.currentTime;
    const startAt = Math.max(now, playTimeRef.current);
    src.start(startAt);
    playTimeRef.current = startAt + audioBuffer.duration;
  }, [ensurePlayContext, ensurePlayAnalyser]);

  const playEncoded = useCallback((chunks: ArrayBuffer[], format?: string) => {
    if (chunks.length === 0) return;
    const generation = playbackGenerationRef.current;
    encodedQueueRef.current = encodedQueueRef.current.then(async () => {
      if (generation !== playbackGenerationRef.current) return;
      const blob = new Blob(chunks, { type: audioMime(format) });
      try {
        const ctx = ensurePlayContext();
        const decoded = await ctx.decodeAudioData(await blob.arrayBuffer());
        if (generation !== playbackGenerationRef.current) return;
        const src = ctx.createBufferSource();
        src.buffer = decoded;
        src.connect(ensurePlayAnalyser(ctx));
        const now = ctx.currentTime;
        const startAt = Math.max(now, playTimeRef.current);
        src.start(startAt);
        playTimeRef.current = startAt + decoded.duration;
        await new Promise<void>((resolve) => { src.onended = () => resolve(); });
      } catch {
        await new Promise<void>((resolve) => {
          const url = URL.createObjectURL(blob);
          const audio = new Audio(url);
          playingElementsRef.current.add(audio);
          const cleanup = () => {
            playingElementsRef.current.delete(audio);
            URL.revokeObjectURL(url);
            resolve();
          };
          audio.onended = cleanup;
          audio.onerror = cleanup;
          void audio.play().catch(() => cleanup());
        });
      }
    });
  }, [ensurePlayContext, ensurePlayAnalyser]);

  const handleServer = useCallback((msg: VoiceServerMessageV3) => {
    console.log('[Voice-V3] server', msg.type, msg);
    switch (msg.type) {
      case 'READY':
        sttLog('server.ready', { audioFormat: msg.audioFormat, turnId: msg.turnId });
        break;
      case 'SESSION':
        sttLog('server.session', { sessionId: msg.sessionId });
        if (msg.sessionId) {
          sessionIdRef.current = msg.sessionId;
          setSessionIdState(msg.sessionId);
        }
        break;
      case 'STATE':
        sttLog('server.state', { from: voiceStateRef.current, to: msg.state, turnId: msg.turnId });
        if (msg.state) setVoiceState(msg.state as VoiceStateV3);
        break;
      case 'ASR_PARTIAL':
        sttLog('asr.partial', {
          turnId: msg.turnId,
          length: (msg.text || '').length,
          text: msg.text || '',
        });
        break;
      case 'ASR_FINAL': {
        const rawText = (msg.text || '').trim();
        const displayText = getVoiceAsrDisplayText(msg);
        sttLog('asr.final', {
          turnId: msg.turnId,
          length: displayText.length,
          text: displayText,
          rawText,
          optimized: displayText !== rawText,
        });
        break;
      }
      case 'TTS_BEGIN':
        ttsFormatRef.current = msg.audioFormat || 'pcm-16000hz-mono-16bit';
        if (!isPcmFormat(ttsFormatRef.current)) encodedChunksRef.current = [];
        break;
      case 'TTS_END':
        if (isPcmFormat(ttsFormatRef.current)) {
          playTimeRef.current = Math.max(playTimeRef.current, (playCtxRef.current?.currentTime ?? 0) + 0.01);
        } else {
          playEncoded(encodedChunksRef.current.slice(), ttsFormatRef.current);
          encodedChunksRef.current = [];
        }
        break;
      case 'ERROR':
        sttLog('server.error', {
          code: msg.code,
          recoverable: msg.recoverable,
          text: msg.text,
          turnId: msg.turnId,
        });
        if (msg.recoverable === false || msg.code !== 6002) {
          setError(msg.text || `code=${msg.code}`);
        }
        break;
      default:
        break;
    }
    optsRef.current.onServer?.(msg);
  }, [playEncoded]);

  const connect = useCallback(async () => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    if (wsRef.current?.readyState === WebSocket.CONNECTING && connectPromiseRef.current) {
      return connectPromiseRef.current;
    }
    const publicAccess = Boolean(optsRef.current.publicAccess);
    if (!publicAccess && !token) throw new Error('Not logged in');
    setStatus('connecting');
    setError(undefined);
    const url = buildVoiceV3WebSocketUrl({ token: token || undefined, publicAccess });
    sttLog('ws.connecting', { url: redactVoiceWsUrl(url), publicAccess });
    const ws = new WebSocket(url);
    ws.binaryType = 'arraybuffer';
    wsRef.current = ws;
    console.log('[Voice-V3] connecting', redactVoiceWsUrl(url));
    const promise = new Promise<void>((resolve, reject) => {
      let settled = false;
      ws.onopen = () => {
        setStatus('open');
        sttLog('ws.open', { url: redactVoiceWsUrl(url), readyState: ws.readyState });
        send({ type: 'HELLO', client: { userAgent: navigator.userAgent } });
        settled = true;
        resolve();
      };
      ws.onerror = () => {
        const err = new Error('WebSocket error');
        setStatus('error');
        setError(err.message);
        sttLog('ws.error', { message: err.message, readyState: ws.readyState });
        optsRef.current.onError?.(err);
        if (!settled) {
          settled = true;
          reject(err);
        }
      };
      ws.onclose = (event) => {
        sttLog('ws.close', { code: event.code, reason: event.reason, wasClean: event.wasClean });
        if (wsRef.current === ws) wsRef.current = null;
        connectPromiseRef.current = null;
        setStatus('closed');
        setVoiceState('CLOSED');
        inCallRef.current = false;
        setInCall(false);
        cleanupCapture();
        stopPlayback();
        if (!settled) {
          settled = true;
          reject(new Error('WebSocket closed before open'));
        }
      };
      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          try {
            const msg = JSON.parse(event.data) as VoiceServerMessageV3;
            if (msg.type === 'ASR_PARTIAL' || msg.type === 'ASR_FINAL') {
              sttLog('ws.recv.asr', { type: msg.type, turnId: msg.turnId, length: (msg.text || '').length });
            }
            handleServer(msg);
          } catch (err) {
            sttLog('ws.recv.parse_error', {
              message: err instanceof Error ? err.message : String(err),
              raw: event.data.slice(0, 300),
            });
          }
        } else if (event.data instanceof ArrayBuffer) {
          if (isPcmFormat(ttsFormatRef.current)) playPcm(event.data, ttsFormatRef.current);
          else encodedChunksRef.current.push(event.data.slice(0));
        }
      };
    });
    connectPromiseRef.current = promise;
    await promise;
  }, [token, cleanupCapture, stopPlayback, handleServer, playPcm, send]);

  const endTurn = useCallback(() => {
    if (!recordingRef.current) {
      sttLog('turn.end.skip', { reason: 'not-recording', state: voiceStateRef.current });
      return;
    }
    sttLog('turn.end.request', {
      turnElapsedMs: Math.round(performance.now() - recordingStartedAtRef.current),
      state: voiceStateRef.current,
    });
    cleanupCapture();
    send({ type: 'AUDIO_END' });
  }, [cleanupCapture, send]);

  const maybeAutoEnd = useCallback((rms: number, peak: number) => {
    if (!recordingRef.current || autoEndingRef.current) return;
    const cfg = { ...DEFAULTS, ...optsRef.current };
    const now = performance.now();
    const elapsed = now - recordingStartedAtRef.current;
    // 预热期不能太长：用户常常点击后立刻说话。旧逻辑 450ms 会把开头人声学习成"噪声"，
    // 导致 hasSpeech 一直为 false，停顿后也不会自动结束这一轮。
    const warmup = elapsed < 120 && !hasSpeechRef.current;

    // 维护最近 ~1.2s 的能量历史，用作"滑动窗口最低水位线"判定。
    const history = energyHistoryRef.current;
    history.push({ t: now, rms, peak });
    while (history.length > 0 && now - history[0].t > 1200) history.shift();
    let windowMinRms = rms;
    let windowMinPeak = peak;
    let windowMaxRms = rms;
    let windowMaxPeak = peak;
    for (let i = 0; i < history.length; i += 1) {
      const item = history[i];
      if (item.rms < windowMinRms) windowMinRms = item.rms;
      if (item.peak < windowMinPeak) windowMinPeak = item.peak;
      if (item.rms > windowMaxRms) windowMaxRms = item.rms;
      if (item.peak > windowMaxPeak) windowMaxPeak = item.peak;
    }
    const windowSpan = history.length > 1 ? now - history[0].t : 0;

    const noiseRms = noiseRmsRef.current;
    const noisePeak = noisePeakRef.current;
    const rmsThreshold = Math.max(cfg.speechRms, noiseRms * 2.0 + 0.0006);
    const peakThreshold = Math.max(cfg.speechPeak, noisePeak * 1.8 + 0.002);
    const speechCandidate = !warmup && (rms >= rmsThreshold || peak >= peakThreshold);
    if (speechCandidate) {
      speechFramesRef.current += 1;
      maxSpeechRmsRef.current = Math.max(maxSpeechRmsRef.current, rms);
      maxSpeechPeakRef.current = Math.max(maxSpeechPeakRef.current, peak);
    } else {
      speechFramesRef.current = Math.max(0, speechFramesRef.current - 1);
    }
    const speech = speechFramesRef.current >= 2
      || rms >= rmsThreshold * 1.6
      || peak >= peakThreshold * 1.6;
    if (speech) {
      hasSpeechRef.current = true;
      lastSpeechAtRef.current = now;
    } else {
      // 不在说话时学习背景噪声底（学习速率小、且对值封顶，避免把人声学进去）。
      const learnRate = hasSpeechRef.current ? 0.025 : 0.06;
      const rmsCap = hasSpeechRef.current ? 0.08 : Math.max(cfg.speechRms * 1.2, noiseRms * 1.5 + 0.0005);
      const peakCap = hasSpeechRef.current ? 0.18 : Math.max(cfg.speechPeak * 1.2, noisePeak * 1.5 + 0.002);
      noiseRmsRef.current = noiseRms * (1 - learnRate) + Math.min(rms, rmsCap) * learnRate;
      noisePeakRef.current = noisePeak * (1 - learnRate) + Math.min(peak, peakCap) * learnRate;
    }

    // 兜底 1：本轮录音曾出现过远高于默认阈值的瞬时电平（>1.6 倍），即视为已经说过话。
    let fallbackHasSpeech = false;
    if (!hasSpeechRef.current
        && elapsed >= 800
        && (maxSpeechRmsRef.current >= cfg.speechRms * 1.6
          || maxSpeechPeakRef.current >= cfg.speechPeak * 1.6)) {
      hasSpeechRef.current = true;
      lastSpeechAtRef.current = lastSpeechAtRef.current || now - cfg.silenceMs / 2;
      fallbackHasSpeech = true;
    }

    // 兜底 2（关键）：滑动窗口"最低水位线"。即使因 AGC / 持续背景音 让 noise floor 学不起来，
    // 只要最近 ~1.2s 内出现过一帧明显低于"窗口峰值的 35%"且接近 cfg.speechRms 的低谷，
    // 就认为出现过停顿，刷新 lastQuietAt；否则刷新为 0。
    // 然后用 lastQuietAt 与 silenceMs 配合，作为传统 lastSpeechAt 之外的另一条触发路径。
    const dropRmsThreshold = Math.max(cfg.speechRms * 0.9, windowMaxRms * 0.35);
    const dropPeakThreshold = Math.max(cfg.speechPeak * 0.9, windowMaxPeak * 0.35);
    const recentlyHadDrop = windowSpan >= 600
      && (windowMinRms <= dropRmsThreshold || windowMinPeak <= dropPeakThreshold);
    if (rms <= dropRmsThreshold && peak <= dropPeakThreshold) {
      lastQuietAtRef.current = now;
    }
    const quietGap = lastQuietAtRef.current ? now - lastQuietAtRef.current : Number.POSITIVE_INFINITY;

    if (now - lastVadDiagnoseAtRef.current >= 800) {
      lastVadDiagnoseAtRef.current = now;
      sttLog('vad.sample', {
        elapsed: Math.round(elapsed),
        rms: +rms.toFixed(4),
        peak: +peak.toFixed(4),
        rmsThr: +rmsThreshold.toFixed(4),
        peakThr: +peakThreshold.toFixed(4),
        noiseRms: +noiseRmsRef.current.toFixed(4),
        noisePeak: +noisePeakRef.current.toFixed(4),
        winMinRms: +windowMinRms.toFixed(4),
        winMaxRms: +windowMaxRms.toFixed(4),
        winMinPeak: +windowMinPeak.toFixed(4),
        winMaxPeak: +windowMaxPeak.toFixed(4),
        speechFrames: speechFramesRef.current,
        hasSpeech: hasSpeechRef.current,
        silenceMs: Math.round(now - lastSpeechAtRef.current),
        quietGap: Number.isFinite(quietGap) ? Math.round(quietGap) : -1,
        recentlyHadDrop,
      });
    }

    // 触发条件 A：经典逻辑——曾说过话 + 最近 silenceMs 没有 speech 帧。
    const classicTrigger = hasSpeechRef.current
      && elapsed >= cfg.minRecordingMs
      && now - lastSpeechAtRef.current >= cfg.silenceMs;
    // 触发条件 B：滑动窗口判定——曾说过话 + 最近 ~1.2s 出现过低谷 +
    // 当前帧仍处在低位（quietGap 在 silenceMs 内被刷新过）。
    const slidingTrigger = hasSpeechRef.current
      && elapsed >= cfg.minRecordingMs
      && recentlyHadDrop
      && quietGap <= 350
      && (now - lastSpeechAtRef.current >= Math.max(cfg.silenceMs * 0.7, 600));
    // 触发条件 C（UI 兑底）：与 UI Level 的显示算法一致——pct = max(rms*10, peak*2) * 120。
    // 若 pct 达到 50%，刷新 lastLoudUiAt；连续 1500ms 未达标则为用户可视的"安静"，直接结束本轮。
    // 不依赖 hasSpeech，但必须本轮确实出现过明显 UI 电平；避免刚开启、用户还没说话就空提交。
    const uiLevel = Math.max(rms * 10, peak * 2);
    if (uiLevel >= 0.4167) {
      lastLoudUiAtRef.current = now;
      hadLoudUiInputRef.current = true;
    }
    const uiSilenceTrigger = elapsed >= cfg.minRecordingMs
      && hadLoudUiInputRef.current
      && now - lastLoudUiAtRef.current >= 1500;

    if (classicTrigger || slidingTrigger || uiSilenceTrigger) {
      autoEndingRef.current = true;
      sttLog('turn.auto_end.silence', {
        elapsed,
        silenceMs: now - lastSpeechAtRef.current,
        rms,
        peak,
        noiseRms: noiseRmsRef.current,
        noisePeak: noisePeakRef.current,
        maxSpeechRms: maxSpeechRmsRef.current,
        maxSpeechPeak: maxSpeechPeakRef.current,
        winMinRms: windowMinRms,
        winMaxRms: windowMaxRms,
        quietGap: Number.isFinite(quietGap) ? quietGap : -1,
        trigger: classicTrigger ? 'classic' : slidingTrigger ? 'sliding' : 'ui-quiet',
        uiQuietMs: Math.round(now - lastLoudUiAtRef.current),
        fallbackHasSpeech,
      });
      optsRef.current.onAutoEnd?.('silence');
      endTurn();
    }
  }, [endTurn]);

  const startTurn = useCallback(async (sid?: string) => {
    await connect();
    if (recordingRef.current) return;
    // 故意不调用 stopPlayback()。
    // HANDS_FREE + autoRestart 模式下，每个 turn 结束（state=IDLE）都会
    // 自动触发 startTurn 进入下一轮。但此时上一轮的 AI 语音其实只是被
    // "调度"到 AudioContext 队列里（playTimeRef 指向未来某个时刻），
    // 物理上还没播放完。如果在这里调 stopPlayback() 会 close() 整个
    // AudioContext，正在播的 BufferSource 立刻被系统终止，用户感受
    // 就是"AI 说一半被掐断"。
    // 真正需要打断 AI 的场景（用户按打断按钮 / 结束通话）走的是 interrupt()
    // 和 endCall() 路径，它们会显式调 stopPlayback()，互不干扰。
    setError(undefined);
    if (sid) {
      sessionIdRef.current = sid;
      setSessionIdState(sid);
    }
    recordingStartedAtRef.current = performance.now();
    const cfg = { ...DEFAULTS, ...optsRef.current };
    sttLog('turn.start', {
      mode: cfg.mode,
      sessionId: sid || sessionIdRef.current,
      kbCount: optsRef.current.kbIds?.length ?? 0,
      globalKb: optsRef.current.globalKb,
      personaId: optsRef.current.personaId,
      inputDeviceId: optsRef.current.inputDeviceId || 'default',
      silenceMs: cfg.silenceMs,
      minRecordingMs: cfg.minRecordingMs,
      maxRecordingMs: cfg.maxRecordingMs,
      speechRms: cfg.speechRms,
      speechPeak: cfg.speechPeak,
    });
    lastSpeechAtRef.current = recordingStartedAtRef.current;
    hasSpeechRef.current = false;
    speechFramesRef.current = 0;
    autoEndingRef.current = false;
    noiseRmsRef.current = 0.001;
    noisePeakRef.current = 0.003;
    maxSpeechRmsRef.current = 0;
    maxSpeechPeakRef.current = 0;
    lastVolumeUpdateAtRef.current = 0;
    lastVadDiagnoseAtRef.current = 0;
    energyHistoryRef.current = [];
    lastQuietAtRef.current = 0;
    lastLoudUiAtRef.current = recordingStartedAtRef.current;
    hadLoudUiInputRef.current = false;
    send({
      type: 'START_TURN',
      mode: cfg.mode,
      sessionId: sessionIdRef.current,
      kbIds: optsRef.current.kbIds,
      personaId: optsRef.current.personaId,
      globalKb: optsRef.current.globalKb,
      sampleRate: 16000,
      inputAudioFormat: 'pcm-16khz-16bit-mono',
    });
    sttLog('capture.start.request', {
      inputDeviceId: optsRef.current.inputDeviceId || 'default',
      targetSampleRate: 16000,
    });
    let debugFrameCount = 0;
    let debugMaxPeak = 0;
    let debugMaxRms = 0;
    let debugSentFrameCount = 0;
    let debugSentBytes = 0;
    let debugAiSpeakingDropCount = 0;
    const capture = await startPcmCapture((frame) => {
      debugFrameCount += 1;
      debugMaxPeak = Math.max(debugMaxPeak, frame.peak || 0);
      debugMaxRms = Math.max(debugMaxRms, frame.rms || 0);
      if (debugFrameCount === 1) {
        sttLog('capture.first_frame', {
          rms: frame.rms,
          peak: frame.peak,
          bytes: frame.buffer.byteLength,
          inputRate: frame.inputRate,
          targetRate: frame.targetRate,
          source: frame.source,
          deviceId: frame.deviceId || 'default',
        });
      }
      if (debugFrameCount % 50 === 0) {
        sttLog('capture.stats', {
          frames: debugFrameCount,
          sentFrames: debugSentFrameCount,
          sentBytes: debugSentBytes,
          aiSpeakingDrops: debugAiSpeakingDropCount,
          rms: frame.rms,
          peak: frame.peak,
          maxRms: debugMaxRms,
          maxPeak: debugMaxPeak,
          inputRate: frame.inputRate,
          targetRate: frame.targetRate,
          source: frame.source,
          deviceId: frame.deviceId,
          noiseRms: noiseRmsRef.current,
          noisePeak: noisePeakRef.current,
        });
      }
      if (debugFrameCount === 100 && debugMaxPeak <= 0) {
        console.warn('[Voice-V3][STT] capture.zero_input', {
          frames: debugFrameCount,
          maxRms: debugMaxRms,
          maxPeak: debugMaxPeak,
        });
      }
      const ws = wsRef.current;
      if (!recordingRef.current || !ws || ws.readyState !== WebSocket.OPEN) return;

      // ─────────────────────────────────────────────────────────────
      // 麦克风采集锁：AI 朗读期间不向后端发送 PCM
      // ─────────────────────────────────────────────────────────────
      // HANDS_FREE + autoRestart 模式下，上一轮 SPEAKING → IDLE 会立刻触发
      // startTurn 启动新一轮 capture。但此时 AI 实际还在物理播放（PCM 已经
      // 调度到 AudioContext，未来时间点上才放完），扬声器漏到麦克风的回声
      // 会被采集上传 → 后端 server VAD（peak ≥ 350 即判定为说话）误触发
      // → endAudio → ASR 空文本 → autoRestart → 又录到回声…无限空 turn 循环。
      //
      // 锁住条件（任一满足即视为"AI 还在说"）：
      //   1) voiceStateRef === 'SPEAKING'：服务端明确告知正在 TTS
      //   2) playTimeRef > ctx.currentTime + epsilon：PCM 物理上未播完
      //      （SPEAKING → IDLE 之后的尾音也属于这段，最关键的兜底）
      // 锁住时不发 frame、不参与 VAD 自动结束、UI 音量归零（避免误导用户）。
      const playCtx = playCtxRef.current;
      const stillPlayingTts = !!playCtx && playTimeRef.current > playCtx.currentTime + 0.05;
      const aiSpeaking = voiceStateRef.current === 'SPEAKING' || stillPlayingTts;
      if (aiSpeaking) {
        debugAiSpeakingDropCount += 1;
        const tNow = performance.now();
        if (debugAiSpeakingDropCount === 1 || debugAiSpeakingDropCount % 50 === 0) {
          sttLog('capture.drop.ai_speaking', {
            drops: debugAiSpeakingDropCount,
            state: voiceStateRef.current,
            stillPlayingTts,
            queuedRemainMs: playCtx ? Math.max(0, Math.round((playTimeRef.current - playCtx.currentTime) * 1000)) : 0,
          });
        }
        if (tNow - lastVolumeUpdateAtRef.current >= 66) {
          lastVolumeUpdateAtRef.current = tNow;
          setVolume(0);
        }
        return;
      }

      ws.send(frame.buffer);
      debugSentFrameCount += 1;
      debugSentBytes += frame.buffer.byteLength;
      if (debugSentFrameCount === 1 || debugSentFrameCount % 50 === 0) {
        sttLog('audio.send.stats', {
          sentFrames: debugSentFrameCount,
          sentBytes: debugSentBytes,
          frameBytes: frame.buffer.byteLength,
          rms: frame.rms,
          peak: frame.peak,
          wsBufferedAmount: ws.bufferedAmount,
        });
      }
      const now = performance.now();
      if (now - lastVolumeUpdateAtRef.current >= 66) {
        lastVolumeUpdateAtRef.current = now;
        const v = Math.min(1, Math.max(frame.rms * 10, frame.peak * 2));
        setVolume(v);
      }
      maybeAutoEnd(frame.rms, frame.peak);
    }, { deviceId: optsRef.current.inputDeviceId || undefined });
    captureRef.current = capture;
    recordingRef.current = true;
    setRecording(true);
    sttLog('capture.started', { source: 'browser', recording: true });
    maxTimerRef.current = window.setTimeout(() => {
      if (!recordingRef.current) return;
      autoEndingRef.current = true;
      sttLog('turn.auto_end.max_length', {
        maxRecordingMs: cfg.maxRecordingMs,
        elapsedMs: Math.round(performance.now() - recordingStartedAtRef.current),
      });
      optsRef.current.onAutoEnd?.('maxLength');
      endTurn();
    }, cfg.maxRecordingMs);
  }, [connect, stopPlayback, send, maybeAutoEnd, endTurn]);

  const startCall = useCallback(async (sid?: string) => {
    sttLog('call.start', { sessionId: sid || sessionIdRef.current });
    inCallRef.current = true;
    setInCall(true);
    try {
      await startTurn(sid);
    } catch (err) {
      // connect / 麦克风权限 / startPcmCapture 任何一步失败时，必须把 inCall 拨回 false，
      // 否则 UI 会进入"通话中但实际没在录音也没连服务器"的死锁状态——只能手动挂断恢复。
      inCallRef.current = false;
      setInCall(false);
      sttLog('call.start.error', { message: err instanceof Error ? err.message : String(err) });
      throw err;
    }
  }, [startTurn]);

  const interrupt = useCallback(() => {
    sttLog('turn.interrupt', { state: voiceStateRef.current });
    cleanupCapture();
    stopPlayback();
    send({ type: 'INTERRUPT' });
  }, [cleanupCapture, stopPlayback, send]);

  const endCall = useCallback(() => {
    sttLog('call.end', {
      state: voiceStateRef.current,
      recording: recordingRef.current,
      sessionId: sessionIdRef.current,
    });
    inCallRef.current = false;
    setInCall(false);
    if (recordingRef.current) endTurn();
    // 用户点击"结束通话"是强信号——无论当前 voiceState 是什么，都必须立刻闭嘴。
    //
    // 之前只在 voiceState === 'SPEAKING'/'THINKING' 时才 interrupt，存在两个洞：
    //   1) voiceState 是 React state，闭包里读到的可能滞后于真实状态；
    //   2) 修复"AI 被掐断"的 bug 后，SPEAKING → IDLE 之间 AI 还在物理播放，
    //      但 voiceState 已经是 IDLE，根本不会进 if，stopPlayback 永远不被调，
    //      用户感受就是"我都点了挂断 AI 还在自顾自说"。
    // 现在无条件关 AudioContext + 清空播放队列；如果服务端确实还在 thinking/speaking，
    // 再发 INTERRUPT 让后端取消整轮（cancel LLM future + cancel TTS task）。
    stopPlayback();
    if (voiceStateRef.current === 'SPEAKING' || voiceStateRef.current === 'THINKING') {
      send({ type: 'INTERRUPT' });
    }
  }, [endTurn, stopPlayback, send]);

  const resetConversation = useCallback(() => {
    sttLog('conversation.reset', { sessionId: sessionIdRef.current });
    cleanupCapture();
    stopPlayback();
    send({ type: 'RESET' });
    setError(undefined);
    setSessionIdState(undefined);
    sessionIdRef.current = undefined;
  }, [cleanupCapture, stopPlayback, send]);

  const disconnect = useCallback(() => {
    sttLog('ws.disconnect.request', {
      state: voiceStateRef.current,
      recording: recordingRef.current,
      sessionId: sessionIdRef.current,
    });
    cleanupCapture();
    stopPlayback();
    const ws = wsRef.current;
    wsRef.current = null;
    if (ws && ws.readyState <= WebSocket.OPEN) ws.close(1000, 'client');
    setStatus('closed');
  }, [cleanupCapture, stopPlayback]);

  const setSessionId = useCallback((sid?: string) => {
    sessionIdRef.current = sid;
    setSessionIdState(sid);
  }, []);

  // 卸载清理 —— 用 ref 稳住最新的 disconnect 引用，effect 自身依赖空数组只在挂载/卸载时触发。
  // 这样未来即使 disconnect 的 useCallback 依赖链发生变化（比如有人加了状态依赖），也不会因为
  // 引用每次 render 都变而把 WebSocket 在组件存活期间反复关闭重连。
  const disconnectRef = useRef(disconnect);
  disconnectRef.current = disconnect;
  useEffect(() => () => { disconnectRef.current(); }, []);

  useEffect(() => {
    const prev = prevStateRef.current;
    prevStateRef.current = voiceState;
    const cfg = { ...DEFAULTS, ...optsRef.current };
    if (!cfg.autoRestart || !inCallRef.current || recordingRef.current || voiceState !== 'IDLE') return;
    if (prev !== 'SPEAKING' && prev !== 'THINKING' && prev !== 'LISTENING') return;
    let cancelled = false;
    const restart = async () => {
      try { await encodedQueueRef.current; } catch {}
      const ctx = playCtxRef.current;
      if (ctx) {
        const remainMs = Math.max(0, (playTimeRef.current - ctx.currentTime) * 1000);
        if (remainMs > 0) await new Promise<void>((resolve) => window.setTimeout(resolve, Math.min(remainMs, 5000) + 250));
      } else {
        await new Promise<void>((resolve) => window.setTimeout(resolve, 250));
      }
      if (cancelled || !inCallRef.current || recordingRef.current) return;
      try { await startTurn(); } catch (err) {
        inCallRef.current = false;
        setInCall(false);
        optsRef.current.onError?.(err as Error);
      }
    };
    void restart();
    return () => { cancelled = true; };
  }, [voiceState, startTurn]);

  const getPlaybackClock = useCallback(() => {
    const ctx = playCtxRef.current;
    if (!ctx) return null;
    return { currentTime: ctx.currentTime, queuedTime: playTimeRef.current };
  }, []);

  return {
    status,
    voiceState,
    recording,
    inCall,
    error,
    sessionId,
    volume,
    playbackVolume,
    connect,
    disconnect,
    startTurn,
    endTurn,
    startCall,
    endCall,
    interrupt,
    resetConversation,
    setSessionId,
    getPlaybackClock,
  };
}
