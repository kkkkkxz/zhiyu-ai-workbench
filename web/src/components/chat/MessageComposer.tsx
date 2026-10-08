import { useEffect, useRef } from 'react';
import { Phone, Send, Square } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

interface MessageComposerProps {
  value: string;
  onChange: (v: string) => void;
  onSubmit: () => void;
  onAbort?: () => void;
  loading?: boolean;
  placeholder?: string;
  hint?: React.ReactNode;
  /** 点击“语音通话”按钮 */
  onCall?: () => void | Promise<void>;
}

export function MessageComposer({
  value,
  onChange,
  onSubmit,
  onAbort,
  loading,
  placeholder = '问点什么…  Enter 发送 · Shift+Enter 换行',
  hint,
  onCall,
}: MessageComposerProps) {
  const taRef = useRef<HTMLTextAreaElement | null>(null);

  // 自适应高度
  useEffect(() => {
    const ta = taRef.current;
    if (!ta) return;
    ta.style.height = 'auto';
    ta.style.height = `${Math.min(ta.scrollHeight, 240)}px`;
  }, [value]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      if (!loading && value.trim()) onSubmit();
    }
  };

  return (
    <div className="border-t bg-background/80 px-4 py-3 backdrop-blur-xl">
      <div className="mx-auto max-w-3xl">
        <div
          className={cn(
            'flex items-end gap-2 rounded-2xl border bg-background/90 p-2 shadow-sm backdrop-blur-md transition-shadow',
            'focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/30',
          )}
        >
          <Textarea
            ref={taRef}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={onKeyDown}
            rows={1}
            placeholder={placeholder}
            disabled={loading}
            className="max-h-[240px] min-h-[40px] flex-1 resize-none border-0 bg-transparent px-2 py-2 text-base shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
          />
          {onCall ? (
            <Button
              type="button"
              variant="outline"
              size="icon"
              onClick={() => void onCall()}
              disabled={loading}
              aria-label="语音通话"
              title="语音通话"
              className="shrink-0"
            >
              <Phone className="h-4 w-4" />
            </Button>
          ) : null}
          {loading ? (
            <Button
              type="button"
              variant="destructive"
              size="icon"
              onClick={onAbort}
              aria-label="停止生成"
            >
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button
              type="button"
              size="icon"
              onClick={onSubmit}
              disabled={!value.trim()}
              aria-label="发送"
            >
              <Send className="h-4 w-4" />
            </Button>
          )}
        </div>
        {hint ? (
          <div className="mt-2 px-2 text-xs text-muted-foreground">{hint}</div>
        ) : null}
      </div>
    </div>
  );
}
