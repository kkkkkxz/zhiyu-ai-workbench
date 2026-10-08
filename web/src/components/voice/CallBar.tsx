import { Loader2, Phone, PhoneOff, Square } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

const STATE_HINT: Record<string, string> = {
  IDLE: '准备中…',
  LISTENING: '正在聆听…说完停顿即可，AI 会自动接话',
  THINKING: '思考中…',
  SPEAKING: 'AI 正在说话…',
  CLOSED: '通话已结束',
};

const STATE_DOT: Record<string, string> = {
  IDLE: 'bg-muted-foreground',
  LISTENING: 'bg-foreground',
  THINKING: 'bg-muted-foreground',
  SPEAKING: 'bg-foreground',
  CLOSED: 'bg-destructive',
};

export interface CallBarProps {
  inCall: boolean;
  voiceState: string;
  recording: boolean;
  volume: number;
  sessionId?: string;
  kbCount: number;
  onToggle: () => void | Promise<void>;
  onInterrupt: () => void;
  error?: string;
}

export function CallBar({
  inCall,
  voiceState,
  recording,
  volume,
  sessionId,
  kbCount,
  onToggle,
  onInterrupt,
  error,
}: CallBarProps) {
  const stateHint = STATE_HINT[voiceState] ?? STATE_HINT.IDLE;
  const stateDot = STATE_DOT[voiceState] ?? STATE_DOT.IDLE;

  return (
    <div className="border-t bg-background px-4 py-4">
      <div className="mx-auto flex max-w-3xl flex-col gap-3">
        {inCall ? (
          <div className="flex items-center gap-3 rounded-2xl border border-foreground/30 bg-foreground/[0.03] p-3">
            <span
              className={cn(
                'h-2.5 w-2.5 shrink-0 rounded-full',
                stateDot,
                'animate-pulse-soft',
              )}
              aria-hidden
            />
            <div className="flex min-w-0 flex-1 flex-col gap-1.5">
              <div className="flex items-center gap-2 text-sm">
                <span className="truncate font-medium">{stateHint}</span>
                {voiceState === 'SPEAKING' ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="ml-auto h-7 gap-1 text-xs"
                    onClick={onInterrupt}
                  >
                    <Square className="h-3 w-3" />
                    打断
                  </Button>
                ) : null}
              </div>
              <LevelBar volume={volume} active={recording} state={voiceState} />
            </div>
          </div>
        ) : null}

        <div className="flex items-center justify-between gap-3">
          <div className="text-xs text-muted-foreground">
            {kbCount > 0
              ? `已挂载 ${kbCount} 个知识库`
              : '未选知识库，模型将使用通用知识作答'}
            {sessionId ? (
              <span className="ml-3 font-mono text-[10px] text-muted-foreground/70">
                session {sessionId}
              </span>
            ) : null}
          </div>

          <button
            type="button"
            onClick={() => void onToggle()}
            aria-label={inCall ? '挂断' : '开始通话'}
            className={cn(
              'group relative flex h-14 items-center gap-2 rounded-full px-6 text-sm font-medium shadow-sm transition-all',
              inCall
                ? 'bg-destructive text-white hover:bg-destructive/90'
                : 'border border-foreground bg-background text-foreground hover:bg-foreground hover:text-background hover:scale-105',
              'active:scale-95',
            )}
          >
            {inCall ? (
              <>
                <PhoneOff className="h-5 w-5" />
                挂断
              </>
            ) : voiceState === 'THINKING' ? (
              <>
                <Loader2 className="h-5 w-5 animate-spin" />
                连接中…
              </>
            ) : (
              <>
                <Phone className="h-5 w-5" />
                开始通话
              </>
            )}
            {inCall && recording ? (
              <span
                className="pointer-events-none absolute inset-0 rounded-full ring-2 ring-destructive/40"
                style={{
                  transform: `scale(${1 + Math.min(volume * 4, 0.5)})`,
                  transition: 'transform 90ms linear',
                }}
              />
            ) : null}
          </button>
        </div>

        {error ? (
          <p className="text-xs text-destructive">⚠ {error}</p>
        ) : null}

        {inCall ? (
          <p className="text-[11px] text-muted-foreground">
            连续通话中：说完停顿即可让 AI 回答；AI 说完会自动重新开麦
          </p>
        ) : null}
      </div>
    </div>
  );
}

function LevelBar({
  volume,
  active,
  state,
}: {
  volume: number;
  active: boolean;
  state: string;
}) {
  const pct = active ? Math.min(100, Math.max(8, volume * 320)) : 0;
  const color =
    state === 'SPEAKING'
      ? 'bg-foreground'
      : state === 'THINKING'
        ? 'bg-muted-foreground'
        : active
          ? 'bg-foreground'
          : 'bg-muted-foreground/30';
  const animated = state === 'SPEAKING' || state === 'THINKING';
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
      <div
        className={cn(
          'h-full rounded-full',
          color,
          animated
            ? 'w-full animate-pulse-soft'
            : 'transition-[width] duration-75 ease-out',
        )}
        style={animated ? undefined : { width: `${pct}%` }}
      />
    </div>
  );
}
