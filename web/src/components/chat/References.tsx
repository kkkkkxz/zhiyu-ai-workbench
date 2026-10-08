import { useState } from 'react';
import { ChevronDown, ChevronRight, Quote } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { ReferenceItem } from '@/types/api';
import { truncate } from '@/lib/format';

export function References({ items }: { items: ReferenceItem[] }) {
  const [open, setOpen] = useState(false);
  if (!items || items.length === 0) return null;

  return (
    <div className="rounded-lg border bg-muted/40 backdrop-blur-md">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs font-medium text-muted-foreground transition-colors hover:text-foreground"
      >
        {open ? (
          <ChevronDown className="h-3.5 w-3.5" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5" />
        )}
        <Quote className="h-3.5 w-3.5" />
        <span>引用了 {items.length} 条片段</span>
      </button>
      {open && (
        <div className="space-y-2 border-t bg-background/60 p-2">
          {items.map((r, i) => (
            <div
              key={r.chunkId}
              className="rounded-md border bg-background/90 p-2.5 text-xs"
            >
              <div className="mb-1.5 flex items-center justify-between gap-2">
                <span className="flex items-center gap-1.5 font-medium">
                  <Badge
                    variant="outline"
                    className="h-5 min-w-[1.5rem] justify-center px-1"
                  >
                    #{i + 1}
                  </Badge>
                  <span className={cn('truncate', 'max-w-[280px]')}>
                    {r.title || '未命名文档'}
                  </span>
                </span>
                <span className="shrink-0 font-mono text-[10px] text-muted-foreground">
                  score {r.score?.toFixed?.(3) ?? '-'}
                </span>
              </div>
              <p className="whitespace-pre-wrap leading-relaxed text-muted-foreground">
                {truncate(r.snippet || '', 320)}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
