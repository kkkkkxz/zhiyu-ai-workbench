import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Bot, Download, FileSearch, FileUp, Paperclip, RefreshCw, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { attachmentApi, type AttachmentAnalysis, type BizAttachment } from '@/api/attachment';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

interface AttachmentPanelProps {
  bizType: string;
  bizId: string;
  title?: string;
}

const CATEGORY_OPTIONS = [
  { value: 'general', label: '通用附件' },
  { value: 'original', label: '原始文件' },
  { value: 'signed', label: '签署/盖章件' },
  { value: 'evidence', label: '处理凭证' },
  { value: 'report', label: '报告' },
];

export function AttachmentPanel({ bizType, bizId, title = '业务附件' }: AttachmentPanelProps) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [items, setItems] = useState<BizAttachment[]>([]);
  const [analyses, setAnalyses] = useState<Record<string, AttachmentAnalysis>>({});
  const [category, setCategory] = useState('general');
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [retrying, setRetrying] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const [list, analysisList] = await Promise.all([
        attachmentApi.list(bizType, bizId),
        attachmentApi.listAnalysis(bizType, bizId),
      ]);
      setItems(list ?? []);
      setAnalyses(Object.fromEntries((analysisList ?? []).map((item) => [item.attachmentId, item])));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, [bizType, bizId]);

  const upload = async (file?: File) => {
    if (!file) return;
    setUploading(true);
    try {
      await attachmentApi.upload(bizType, bizId, file, category);
      toast.success('附件已上传，AI 正在解析判断');
      if (inputRef.current) inputRef.current.value = '';
      await refresh();
    } finally {
      setUploading(false);
    }
  };

  const download = async (id: string) => {
    const result = await attachmentApi.downloadUrl(id);
    window.open(result.url, '_blank', 'noopener,noreferrer');
  };

  const remove = async (id: string) => {
    await attachmentApi.delete(id);
    toast.success('附件已删除');
    await refresh();
  };

  const retryAnalysis = async (id: string) => {
    setRetrying(id);
    try {
      const result = await attachmentApi.retryAnalysis(id);
      setAnalyses((current) => ({ ...current, [id]: result }));
      toast.success('已重新提交 AI 解析');
      window.setTimeout(() => void refresh(), 1500);
    } finally {
      setRetrying(null);
    }
  };

  return (
    <section className="space-y-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2 font-medium">
          <Paperclip className="h-4 w-4" />
          <span>{title}</span>
          <Badge variant="outline">{items.length}</Badge>
        </div>
        <div className="flex flex-wrap gap-2">
          <select
            className="h-9 rounded-md border bg-background px-2 text-sm"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            {CATEGORY_OPTIONS.map((item) => (
              <option key={item.value} value={item.value}>{item.label}</option>
            ))}
          </select>
          <input
            ref={inputRef}
            type="file"
            className="hidden"
            onChange={(e) => void upload(e.target.files?.[0])}
          />
          <Button
            size="sm"
            variant="outline"
            className="gap-1"
            disabled={uploading}
            onClick={() => inputRef.current?.click()}
          >
            <FileUp className="h-4 w-4" />
            {uploading ? '上传中' : '上传'}
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="rounded-md border p-4 text-sm text-muted-foreground">正在加载附件...</div>
      ) : items.length === 0 ? (
        <div className="rounded-md border p-4 text-sm text-muted-foreground">
          暂无附件。合同原件、盖章件、截图、处理凭证都可以放在这里。
        </div>
      ) : (
        <div className="divide-y rounded-md border">
          {items.map((item) => (
            <div key={item.id} className="space-y-3 p-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{item.fileName}</div>
                  <div className="mt-1 flex flex-wrap gap-2 text-xs text-muted-foreground">
                    <span>{formatBytes(item.fileSize)}</span>
                    <span>v{item.versionNo}</span>
                    <span>{categoryLabel(item.category)}</span>
                    <span>{item.createTime?.slice(0, 16).replace('T', ' ')}</span>
                  </div>
                </div>
                <div className="flex gap-1">
                  <Button
                    size="icon"
                    variant="ghost"
                    title="重新 AI 解析"
                    disabled={retrying === item.id}
                    onClick={() => void retryAnalysis(item.id)}
                  >
                    <RefreshCw className={`h-4 w-4 ${retrying === item.id ? 'animate-spin' : ''}`} />
                  </Button>
                  <Button size="icon" variant="ghost" title="下载" onClick={() => void download(item.id)}>
                    <Download className="h-4 w-4" />
                  </Button>
                  <Button size="icon" variant="ghost" title="删除" onClick={() => void remove(item.id)}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
              <AnalysisCard analysis={analyses[item.id]} />
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function AnalysisCard({ analysis }: { analysis?: AttachmentAnalysis }) {
  if (!analysis) {
    return (
      <div className="rounded-md bg-muted/60 px-3 py-2 text-xs text-muted-foreground">
        等待 AI 解析结果生成。
      </div>
    );
  }

  const processing = analysis.status === 'PENDING' || analysis.status === 'PARSING';
  return (
    <div className="rounded-md border bg-muted/30 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={statusVariant(analysis.status)} className="gap-1">
          {processing ? <RefreshCw className="h-3 w-3 animate-spin" /> : <Bot className="h-3 w-3" />}
          {statusLabel(analysis.status)}
        </Badge>
        {analysis.documentType ? (
          <Badge variant="outline" className="gap-1">
            <FileSearch className="h-3 w-3" />
            {analysis.documentType}
          </Badge>
        ) : null}
        {analysis.riskLevel ? (
          <Badge variant={riskVariant(analysis.riskLevel)} className="gap-1">
            <AlertTriangle className="h-3 w-3" />
            {riskLabel(analysis.riskLevel)}
          </Badge>
        ) : null}
        {analysis.analyzedAt ? (
          <span className="text-xs text-muted-foreground">
            {analysis.analyzedAt.slice(0, 16).replace('T', ' ')}
          </span>
        ) : null}
      </div>

      {analysis.summary ? (
        <div className="mt-2 text-sm text-foreground">{analysis.summary}</div>
      ) : processing ? (
        <div className="mt-2 text-sm text-muted-foreground">正在提取文件文本并判断业务风险...</div>
      ) : null}

      {analysis.parseError ? (
        <div className="mt-2 text-xs text-muted-foreground">{analysis.parseError}</div>
      ) : null}

      {analysis.keyFields && Object.keys(analysis.keyFields).length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {Object.entries(analysis.keyFields).slice(0, 8).map(([key, value]) => (
            <Badge key={key} variant="outline">
              {key}: {String(value)}
            </Badge>
          ))}
        </div>
      ) : null}

      <AnalysisList title="缺失项" items={analysis.missingFields} />
      <AnalysisList title="风险点" items={analysis.risks} />
      <AnalysisList title="建议" items={analysis.suggestions} />
      <AnalysisList title="下一步" items={analysis.nextActions} />
    </div>
  );
}

function AnalysisList({ title, items }: { title: string; items?: string[] }) {
  const values = (items ?? []).filter(Boolean).slice(0, 4);
  if (values.length === 0) return null;
  return (
    <div className="mt-2">
      <div className="mb-1 text-xs font-medium text-muted-foreground">{title}</div>
      <div className="flex flex-wrap gap-1.5">
        {values.map((item) => (
          <Badge key={`${title}-${item}`} variant="secondary">{item}</Badge>
        ))}
      </div>
    </div>
  );
}

function categoryLabel(value: string) {
  return CATEGORY_OPTIONS.find((item) => item.value === value)?.label ?? value;
}

function formatBytes(bytes?: number) {
  if (!bytes) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function statusLabel(status: AttachmentAnalysis['status']) {
  const map: Record<AttachmentAnalysis['status'], string> = {
    PENDING: '待解析',
    PARSING: '解析中',
    SUCCESS: '已判断',
    FAILED: '解析失败',
    SKIPPED: '需人工查看',
  };
  return map[status] ?? status;
}

function statusVariant(status: AttachmentAnalysis['status']) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'destructive';
  if (status === 'SKIPPED') return 'warning';
  return 'secondary';
}

function riskLabel(risk: NonNullable<AttachmentAnalysis['riskLevel']>) {
  const map: Record<NonNullable<AttachmentAnalysis['riskLevel']>, string> = {
    LOW: '低风险',
    MEDIUM: '中风险',
    HIGH: '高风险',
  };
  return map[risk] ?? risk;
}

function riskVariant(risk: NonNullable<AttachmentAnalysis['riskLevel']>) {
  if (risk === 'LOW') return 'success';
  if (risk === 'HIGH') return 'destructive';
  return 'warning';
}
