export type CaptureSource = 'worklet' | 'script-processor';

export interface CaptureFrame {
  buffer: ArrayBuffer;
  rms: number;
  peak: number;
  inputRate?: number;
  targetRate?: number;
  source?: CaptureSource;
  deviceId?: string;
}

export interface CaptureOptions {
  deviceId?: string;
  targetRate?: number;
  frameSamples?: number;
  inputGain?: number;
  autoFallbackToScriptProcessor?: boolean;
}

export interface CaptureHandle {
  stop: () => void;
}

export interface AudioInputDevice {
  deviceId: string;
  groupId: string;
  label: string;
}

export interface MicProbeResult {
  hasSignal: boolean;
  maxRms: number;
  maxPeak: number;
  frameCount: number;
  deviceId?: string;
  trackLabel?: string;
  trackSettings?: MediaTrackSettings;
}

const DEFAULT_TARGET_RATE = 16000;
const DEFAULT_FRAME_SAMPLES = 320;
const DEFAULT_INPUT_GAIN = 4;
const WORKLET_ZERO_FRAME_LIMIT = 120;

function sttLog(stage: string, data?: Record<string, unknown>) {
  if (data) console.info(`[Voice-V3][STT] ${stage}`, data);
  else console.info(`[Voice-V3][STT] ${stage}`);
}

function audioContextCtor(): typeof AudioContext {
  const Ctor = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined;
  if (!Ctor) throw new Error('AudioContext is not supported in this browser');
  return Ctor;
}

function createAudioConstraints(deviceId?: string): MediaTrackConstraints {
  // 通话级电话拾音：必须开回声消除（避免 AI 通过扬声器回响被麦克风录回去），
  // 必须开降噪（避免风扇 / 空调等持续噪音持续触发说话判定），
  // 关闭自动增益（AGC 会把背景声放大到 0.05+ 让 VAD 永远判定在说话）。
  const constraints: MediaTrackConstraints = {
    channelCount: { ideal: 1 },
    autoGainControl: false,
    echoCancellation: true,
    noiseSuppression: true,
  };
  if (deviceId) {
    constraints.deviceId = { exact: deviceId };
  }
  return constraints;
}

async function openAudioStream(options: CaptureOptions = {}): Promise<MediaStream> {
  const audio = createAudioConstraints(options.deviceId);
  sttLog('media.get_user_media.request', {
    deviceId: options.deviceId || 'default',
    audio,
  });
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio });
    const track = stream.getAudioTracks()[0];
    sttLog('media.get_user_media.success', {
      deviceId: options.deviceId || 'default',
      trackLabel: track?.label,
      trackSettings: track?.getSettings?.(),
    });
    return stream;
  } catch (err) {
    sttLog('media.get_user_media.error', {
      deviceId: options.deviceId || 'default',
      name: err instanceof DOMException ? err.name : undefined,
      message: err instanceof Error ? err.message : String(err),
    });
    throw err;
  }
}

function stopStream(stream: MediaStream | null | undefined) {
  stream?.getTracks().forEach((track) => track.stop());
}

function frameFromFloat32(
  samples: number[],
  frameSamples: number,
  inputGain: number,
  inputRate: number,
  targetRate: number,
  source: CaptureSource,
  deviceId?: string,
): CaptureFrame {
  const buffer = new ArrayBuffer(frameSamples * 2);
  const view = new DataView(buffer);
  let sumSq = 0;
  let peak = 0;
  for (let j = 0; j < frameSamples; j += 1) {
    const v = Math.max(-1, Math.min(1, (samples[j] || 0) * inputGain));
    sumSq += v * v;
    peak = Math.max(peak, Math.abs(v));
    view.setInt16(j * 2, v < 0 ? v * 0x8000 : v * 0x7fff, true);
  }
  return {
    buffer,
    rms: Math.sqrt(sumSq / frameSamples),
    peak,
    inputRate,
    targetRate,
    source,
    deviceId,
  };
}

export async function startPcmCapture(onFrame: (frame: CaptureFrame) => void, options: CaptureOptions = {}): Promise<CaptureHandle> {
  const targetRate = options.targetRate || DEFAULT_TARGET_RATE;
  const frameSamples = options.frameSamples || DEFAULT_FRAME_SAMPLES;
  const inputGain = options.inputGain || DEFAULT_INPUT_GAIN;
  const autoFallback = options.autoFallbackToScriptProcessor !== false;

  const stream = await openAudioStream(options);
  const Ctor = audioContextCtor();
  const ctx = new Ctor();
  if (ctx.state === 'suspended') await ctx.resume().catch(() => undefined);

  let source: MediaStreamAudioSourceNode | null = null;
  let node: AudioWorkletNode | null = null;
  let sink: GainNode | null = null;
  let fallbackHandle: CaptureHandle | null = null;
  let stopped = false;
  let switchingToFallback = false;

  const track = stream.getAudioTracks()[0];
  const deviceId = track?.getSettings?.().deviceId || options.deviceId;

  const stopWorkletGraph = () => {
    try { node?.port.postMessage({ type: 'stop' }); } catch {}
    try { node?.disconnect(); } catch {}
    try { sink?.disconnect(); } catch {}
    try { source?.disconnect(); } catch {}
    stopStream(stream);
    void ctx.close().catch(() => undefined);
  };

  const switchToScriptProcessor = () => {
    if (!autoFallback || stopped || switchingToFallback || fallbackHandle) return;
    switchingToFallback = true;
    console.warn('[Voice-V3] AudioWorklet produced only zero samples; switching to ScriptProcessor capture fallback.', {
      deviceId,
      trackLabel: track?.label,
      trackSettings: track?.getSettings?.(),
    });
    stopWorkletGraph();
    void startScriptProcessorCapture(onFrame, options)
      .then((handle) => {
        if (stopped) handle.stop();
        else fallbackHandle = handle;
      })
      .catch((err) => {
        console.error('[Voice-V3] ScriptProcessor capture fallback failed', err);
      });
  };

  try {
    await ctx.audioWorklet.addModule('/voice-pcm-worklet.js?v=5');
    source = ctx.createMediaStreamSource(stream);
    node = new AudioWorkletNode(ctx, 'zwsk-voice-pcm-worklet', {
      numberOfInputs: 1,
      numberOfOutputs: 1,
      outputChannelCount: [1],
      processorOptions: { targetRate, frameSamples, inputGain },
    });
    sink = ctx.createGain();
    sink.gain.value = 1;

    let checkedFrames = 0;
    let maxPeak = 0;
    let maxRms = 0;
    node.port.onmessage = (event: MessageEvent) => {
      if (stopped || switchingToFallback || fallbackHandle) return;
      const data = event.data || {};
      if (data.type === 'audio' && data.buffer instanceof ArrayBuffer) {
        const frame: CaptureFrame = {
          buffer: data.buffer,
          rms: Number(data.rms || 0),
          peak: Number(data.peak || 0),
          inputRate: Number(data.inputRate || ctx.sampleRate),
          targetRate: Number(data.targetRate || targetRate),
          source: 'worklet',
          deviceId,
        };
        checkedFrames += 1;
        maxPeak = Math.max(maxPeak, frame.peak || 0);
        maxRms = Math.max(maxRms, frame.rms || 0);
        onFrame(frame);
        if (checkedFrames === WORKLET_ZERO_FRAME_LIMIT && maxPeak <= 0 && maxRms <= 0) {
          switchToScriptProcessor();
        }
      }
    };

    source.connect(node);
    // Keep the AudioWorklet connected to an output chain so Chromium continues pulling mic input.
    // The processor writes silence to its output, so this does not play microphone audio.
    node.connect(sink);
    sink.connect(ctx.destination);

    console.info('[Voice-V3] capture started', {
      source: 'worklet',
      ctxSampleRate: ctx.sampleRate,
      trackLabel: track?.label,
      trackSettings: track?.getSettings?.(),
    });

    return {
      stop: () => {
        stopped = true;
        fallbackHandle?.stop();
        fallbackHandle = null;
        stopWorkletGraph();
      },
    };
  } catch (err) {
    stopWorkletGraph();
    console.warn('[Voice-V3] AudioWorklet unavailable; using ScriptProcessor capture fallback.', err);
    return startScriptProcessorCapture(onFrame, options);
  }
}

async function startScriptProcessorCapture(onFrame: (frame: CaptureFrame) => void, options: CaptureOptions = {}): Promise<CaptureHandle> {
  const targetRate = options.targetRate || DEFAULT_TARGET_RATE;
  const frameSamples = options.frameSamples || DEFAULT_FRAME_SAMPLES;
  const inputGain = options.inputGain || DEFAULT_INPUT_GAIN;
  const stream = await openAudioStream(options);
  const Ctor = audioContextCtor();
  const ctx = new Ctor();
  if (ctx.state === 'suspended') await ctx.resume().catch(() => undefined);
  const source = ctx.createMediaStreamSource(stream);
  const proc = ctx.createScriptProcessor(2048, 1, 1);
  let pending: number[] = [];
  let readIndex = 0;
  let out: number[] = [];
  const ratio = ctx.sampleRate / targetRate;
  const track = stream.getAudioTracks()[0];
  const deviceId = track?.getSettings?.().deviceId || options.deviceId;

  proc.onaudioprocess = (e) => {
    const output = e.outputBuffer.getChannelData(0);
    output.fill(0);
    const ch = e.inputBuffer.getChannelData(0);
    for (let i = 0; i < ch.length; i += 1) pending.push(ch[i]);
    while (readIndex + ratio < pending.length) {
      const idx = Math.floor(readIndex);
      const nextIdx = Math.min(idx + 1, pending.length - 1);
      const frac = readIndex - idx;
      out.push(Math.max(-1, Math.min(1, pending[idx] * (1 - frac) + pending[nextIdx] * frac)));
      readIndex += ratio;
      if (out.length >= frameSamples) {
        const frame = frameFromFloat32(out, frameSamples, inputGain, ctx.sampleRate, targetRate, 'script-processor', deviceId);
        out = out.slice(frameSamples);
        onFrame(frame);
      }
    }
    if (readIndex > 2048) {
      const drop = Math.floor(readIndex);
      pending = pending.slice(drop);
      readIndex -= drop;
    }
  };
  source.connect(proc);
  proc.connect(ctx.destination);
  console.info('[Voice-V3] capture started', {
    source: 'script-processor',
    ctxSampleRate: ctx.sampleRate,
    trackLabel: track?.label,
    trackSettings: track?.getSettings?.(),
  });
  return {
    stop: () => {
      try { proc.disconnect(); } catch {}
      try { source.disconnect(); } catch {}
      stopStream(stream);
      void ctx.close().catch(() => undefined);
    },
  };
}

export async function listAudioInputDevices(): Promise<AudioInputDevice[]> {
  if (!navigator.mediaDevices?.enumerateDevices) return [];
  let devices = await navigator.mediaDevices.enumerateDevices();
  let inputs = devices.filter((device) => device.kind === 'audioinput');

  if (inputs.length > 0 && inputs.every((device) => !device.label) && navigator.mediaDevices.getUserMedia) {
    let stream: MediaStream | null = null;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      devices = await navigator.mediaDevices.enumerateDevices();
      inputs = devices.filter((device) => device.kind === 'audioinput');
    } catch {
      // Permission may still be pending/denied; return unlabeled devices if available.
    } finally {
      stopStream(stream);
    }
  }

  return inputs.map((device, index) => ({
    deviceId: device.deviceId,
    groupId: device.groupId,
    label: device.label || 'Microphone ' + (index + 1),
  }));
}

export async function probeMicrophoneLevel(deviceId?: string, durationMs = 2600): Promise<MicProbeResult> {
  let stream: MediaStream | null = null;
  let ctx: AudioContext | null = null;
  let source: MediaStreamAudioSourceNode | null = null;
  let proc: ScriptProcessorNode | null = null;
  let frameCount = 0;
  let maxPeak = 0;
  let maxRms = 0;
  let track: MediaStreamTrack | undefined;

  try {
    stream = await openAudioStream({ deviceId });
    track = stream.getAudioTracks()[0];
    const Ctor = audioContextCtor();
    ctx = new Ctor();
    if (ctx.state === 'suspended') await ctx.resume().catch(() => undefined);
    source = ctx.createMediaStreamSource(stream);
    proc = ctx.createScriptProcessor(2048, 1, 1);
    proc.onaudioprocess = (e) => {
      const output = e.outputBuffer.getChannelData(0);
      output.fill(0);
      const ch = e.inputBuffer.getChannelData(0);
      let sumSq = 0;
      let peak = 0;
      for (let i = 0; i < ch.length; i += 1) {
        const v = ch[i];
        sumSq += v * v;
        peak = Math.max(peak, Math.abs(v));
      }
      const rms = Math.sqrt(sumSq / ch.length);
      frameCount += 1;
      maxPeak = Math.max(maxPeak, peak);
      maxRms = Math.max(maxRms, rms);
    };
    source.connect(proc);
    proc.connect(ctx.destination);
    await new Promise((resolve) => window.setTimeout(resolve, durationMs));
    return {
      hasSignal: maxPeak >= 0.003 || maxRms >= 0.0005,
      maxPeak,
      maxRms,
      frameCount,
      deviceId: track?.getSettings?.().deviceId || deviceId,
      trackLabel: track?.label,
      trackSettings: track?.getSettings?.(),
    };
  } finally {
    try { proc?.disconnect(); } catch {}
    try { source?.disconnect(); } catch {}
    stopStream(stream);
    void ctx?.close().catch(() => undefined);
  }
}

export function isPcmFormat(format?: string) {
  return !format || format.toLowerCase().includes('pcm');
}

export function audioMime(format?: string) {
  const f = (format || '').toLowerCase();
  if (f.includes('mp3') || f.includes('mpeg')) return 'audio/mpeg';
  if (f.includes('wav')) return 'audio/wav';
  if (f.includes('opus')) return 'audio/ogg; codecs=opus';
  if (f.includes('ogg')) return 'audio/ogg';
  return 'application/octet-stream';
}

export function sampleRateFromFormat(format?: string) {
  const f = (format || '').toLowerCase();
  const hz = f.match(/(\d{4,6})\s*hz/);
  if (hz) return Number(hz[1]);
  const khz = f.match(/(\d{2,3})\s*k/);
  if (khz) return Number(khz[1]) * 1000;
  return 24000;
}
