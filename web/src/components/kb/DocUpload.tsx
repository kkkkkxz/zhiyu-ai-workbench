import { useRef, useState } from 'react';
import { Upload, X } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { docApi } from '@/api/doc';
import type { KnowledgeDocument } from '@/types/api';
import { formatBytes } from '@/lib/format';

const ACCEPT =
  '.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.html,.htm,.rtf,.txt,.md,.markdown,.csv';
const MAX_BYTES = 50 * 1024 * 1024;

interface DocUploadProps {
  kbId: string;
  onUploaded: (doc: KnowledgeDocument) => void;
}

interface UploadingItem {
  id: string;
  file: File;
  percent: number;
  error?: string;
  done?: boolean;
}

export function DocUpload({ kbId, onUploaded }: DocUploadProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [items, setItems] = useState<UploadingItem[]>([]);

  const pickFiles = () => inputRef.current?.click();

  const handleFiles = async (files: FileList | File[]) => {
    const arr = Array.from(files);
    if (arr.length === 0) return;

    for (const file of arr) {
      if (file.size > MAX_BYTES) {
        toast.error(`“${file.name}”超过 50MB，已跳过`);
        continue;
      }
      const id = `${file.name}-${file.size}-${Date.now()}-${Math.random()}`;
      setItems((prev) => [{ id, file, percent: 0 }, ...prev]);
      try {
        const doc = await docApi.uploadFile(kbId, file, (percent) => {
          setItems((prev) => prev.map((item) => (item.id === id ? { ...item, percent } : item)));
        });
        setItems((prev) => prev.map((item) => (item.id === id ? { ...item, percent: 100, done: true } : item)));
        toast.success(`“${file.name}”已上传，正在解析`);
        onUploaded(doc);
        setTimeout(() => {
          setItems((prev) => prev.filter((item) => item.id !== id));
        }, 2500);
      } catch (error) {
        const message = (error as Error)?.message || '上传失败';
        setItems((prev) => prev.map((item) => (item.id === id ? { ...item, error: message } : item)));
      }
    }
  };

  return (
    <div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          e.stopPropagation();
          setDragOver(true);
        }}
        onDragLeave={(e) => {
          e.preventDefault();
          setDragOver(false);
        }}
        onDrop={(e) => {
          e.preventDefault();
          setDragOver(false);
          if (e.dataTransfer.files?.length) {
            void handleFiles(e.dataTransfer.files);
          }
        }}
        className={cn(
          'flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed bg-muted/30 px-6 py-10 text-center transition-colors',
          dragOver ? 'border-primary bg-primary/5' : 'border-border/70',
        )}
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
          <Upload className="h-5 w-5" />
        </div>
        <p className="text-sm">
          <strong>点击上传</strong> 或拖拽文件到这里
        </p>
        <p className="text-xs text-muted-foreground">
          支持 PDF / Word / PPT / Excel / HTML / Markdown / TXT，单文件最大 50MB
        </p>
        <Button variant="outline" size="sm" onClick={pickFiles}>
          选择文件
        </Button>
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          accept={ACCEPT}
          multiple
          onChange={(e) => {
            if (e.target.files) {
              void handleFiles(e.target.files);
              e.target.value = '';
            }
          }}
        />
      </div>

      {items.length > 0 ? (
        <ul className="mt-3 space-y-2">
          {items.map((item) => (
            <li
              key={item.id}
              className={cn(
                'flex items-center gap-3 rounded-lg border bg-card px-3 py-2 text-sm',
                item.error && 'border-destructive bg-destructive/5',
              )}
            >
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <span className="truncate font-medium">{item.file.name}</span>
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {formatBytes(item.file.size)}
                  </span>
                </div>
                <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn(
                      'h-full transition-all',
                      item.error ? 'bg-destructive' : item.done ? 'bg-success' : 'bg-primary',
                    )}
                    style={{ width: `${item.percent}%` }}
                  />
                </div>
                {item.error ? (
                  <div className="mt-1 text-xs text-destructive">{item.error}</div>
                ) : null}
              </div>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={() => setItems((prev) => prev.filter((current) => current.id !== item.id))}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
