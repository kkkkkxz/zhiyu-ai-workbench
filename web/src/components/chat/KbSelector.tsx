import { useEffect, useState } from 'react';
import { Database, Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { kbApi } from '@/api/kb';
import type { KnowledgeBase } from '@/types/api';
import { truncate } from '@/lib/format';

interface KbSelectorProps {
  value: string[];
  onChange: (next: string[]) => void;
}

export function KbSelector({ value, onChange }: KbSelectorProps) {
  const [list, setList] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const r = await kbApi.list();
        setList(r ?? []);
      } catch {
        setList([]);
      } finally {
        setLoading(false);
      }
    };
    if (open && list.length === 0) {
      void load();
    }
  }, [open]);

  const toggle = (id: string) => {
    if (value.includes(id)) onChange(value.filter((x) => x !== id));
    else onChange([...value, id]);
  };

  const selected = list.filter((kb) => value.includes(kb.id));
  const label =
    value.length === 0
      ? '未选知识库'
      : selected.length === 0
        ? `已选 ${value.length}`
        : selected.length === 1
          ? truncate(selected[0]!.name, 18)
          : `${truncate(selected[0]!.name, 12)} +${selected.length - 1}`;

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="h-8 gap-1.5">
          <Database className="h-3.5 w-3.5" />
          <span className="max-w-[180px] truncate">{label}</span>
          {value.length > 0 ? (
            <Badge
              variant="secondary"
              className="h-4 min-w-[1rem] justify-center px-1 text-[10px]"
            >
              {value.length}
            </Badge>
          ) : null}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-72">
        <DropdownMenuLabel>选择参与检索的知识库</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-4 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> 加载中
          </div>
        ) : list.length === 0 ? (
          <div className="px-3 py-4 text-center text-xs text-muted-foreground">
            暂无知识库
          </div>
        ) : (
          list.map((kb) => (
            <DropdownMenuCheckboxItem
              key={kb.id}
              checked={value.includes(kb.id)}
              onCheckedChange={() => toggle(kb.id)}
              onSelect={(e) => e.preventDefault()}
            >
              <div className="flex flex-col">
                <span className="font-medium">{truncate(kb.name, 28)}</span>
                {kb.description ? (
                  <span className="text-[11px] text-muted-foreground">
                    {truncate(kb.description, 36)}
                  </span>
                ) : null}
              </div>
            </DropdownMenuCheckboxItem>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
