export type VoiceModeV3 = 'PTT' | 'HANDS_FREE' | 'VISUAL';
export type VoiceStateV3 = 'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING' | 'CLOSED';
export type VoiceWsStatusV3 = 'idle' | 'connecting' | 'open' | 'closed' | 'error';

export interface VoiceClientMessageV3 {
  type: 'HELLO' | 'START_TURN' | 'AUDIO_END' | 'INTERRUPT' | 'RESET' | 'PING';
  mode?: VoiceModeV3;
  sessionId?: string | number;
  kbIds?: (string | number)[];
  personaId?: string;
  globalKb?: boolean;
  sampleRate?: number;
  inputAudioFormat?: string;
  client?: Record<string, unknown>;
}

export interface VoiceReferenceV3 {
  chunkId?: string | number;
  documentId?: string | number;
  kbId?: string | number;
  title?: string;
  score?: number;
  snippet?: string;
}

export interface VoiceServerMessageV3 {
  type:
    | 'READY'
    | 'SESSION'
    | 'STATE'
    | 'ASR_PARTIAL'
    | 'ASR_FINAL'
    | 'REFERENCES'
    | 'LLM_DELTA'
    | 'LLM_DONE'
    | 'TTS_BEGIN'
    | 'TTS_END'
    | 'TURN_END'
    | 'ERROR'
    | 'PONG';
  state?: VoiceStateV3 | string;
  text?: string;
  semanticText?: string;
  optimizedText?: string;
  normalizedText?: string;
  rewrittenText?: string;
  displayText?: string;
  queryText?: string;
  originalText?: string;
  rawText?: string;
  turnId?: string;
  sessionId?: string;
  messageId?: string;
  audioFormat?: string;
  code?: number;
  recoverable?: boolean;
  references?: VoiceReferenceV3[];
  meta?: Record<string, unknown>;
}

const ASR_DISPLAY_TEXT_FIELDS = [
  'semanticText',
  'optimizedText',
  'normalizedText',
  'rewrittenText',
  'displayText',
  'queryText',
  'semanticQuery',
  'optimizedQuery',
] as const;

function cleanVoiceText(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

export function getVoiceAsrDisplayText(msg: VoiceServerMessageV3) {
  for (const key of ASR_DISPLAY_TEXT_FIELDS) {
    const text = cleanVoiceText(msg[key as keyof VoiceServerMessageV3]);
    if (text) return text;
  }
  for (const key of ASR_DISPLAY_TEXT_FIELDS) {
    const text = cleanVoiceText(msg.meta?.[key]);
    if (text) return text;
  }
  return cleanVoiceText(msg.text);
}

export const VOICE_V3_WS_PATH = '/api/voice/ws/v3';

export function buildVoiceV3WebSocketUrl(options: { token?: string; publicAccess?: boolean } = {}) {
  // Default to same-origin so HTTPS tunnel / mobile testing can reuse the Vite /api proxy.
  // A direct backend URL can still be supplied with VITE_VOICE_WS_BASE_URL when needed.
  const configuredBase = String(import.meta.env.VITE_VOICE_WS_BASE_URL || '').trim();
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const base = configuredBase || `${proto}://${window.location.host}`;
  const url = new URL(VOICE_V3_WS_PATH, base);
  if (url.protocol === 'http:') url.protocol = 'ws:';
  if (url.protocol === 'https:') url.protocol = 'wss:';
  if (options.publicAccess) {
    url.searchParams.set('public', '1');
  } else if (options.token) {
    url.searchParams.set('token', options.token);
  }
  return url.toString();
}
