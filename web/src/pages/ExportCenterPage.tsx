import { useEffect, useMemo, useState } from 'react';
import { Download, FileDown, RefreshCcw, Send } from 'lucide-react';
import { toast } from 'sonner';

import { exportApi, type ExportBizType, type ExportTask } from '@/api/export';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

const BIZ_OPTIONS: { value: ExportBizType; label: string }[] = [
  { value: 'customer', label: '客户' },
  { value: 'contract', label: '合同' },
  { value: 'work_order', label: '工单' },
  { value: 'session', label: '会话' },
];

const STATUS_LABEL: Record<string, string> = {
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  failed: '失败',
};

const STATUS_VARIANT: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
  pending: 'secondary',
  processing: 'default',
  completed: 'default',
  failed: 'destructive',
};

export default function ExportCenterPage() {
  const [bizType, setBizType] = useState<ExportBizType>('customer');
  const [bizId, setBizId] = useState('');
  const [format, setFormat] = useState<'pdf' | 'docx'>('pdf');
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [tasks, setTasks] = useState<ExportTask[]>([]);
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<ExportTask | null>(null);
  const [filterStatus, setFilterStatus] = useState<'all' | 'pending' | 'processing' | 'completed' | 'failed'>('all');

  useEffect(() => {
    const prevTitle = document.title;
    document.title = '导出中心';
    return () => {
      document.title = prevTitle;
    };
  }, []);

  const load = async (p = page) => {
    setLoading(true);
    try {
      const res = await exportApi.myTasks(p, 20);
      const records = res.records ?? [];
      setTasks(records);
      setTotal(res.total ?? 0);
      setSelected((prev) => {
        if (!prev) return prev;
        return records.find((item) => item.id === prev.id) ?? prev;
      });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [page]);

  const visibleTasks = useMemo(() => {
    if (filterStatus === 'all') return tasks;
    return tasks.filter((task) => task.status === filterStatus);
  }, [tasks, filterStatus]);

  const createTask = async () => {
    if (!bizId.trim()) {
      toast.error('请先填写业务 ID');
      return;
    }
    setCreating(true);
    try {
      await exportApi.create(bizType, bizId.trim(), format);
      toast.success('导出任务已创建');
      setBizId('');
      await load(1);
      setPage(1);
    } finally {
      setCreating(false);
    }
  };

  const openDownload = async (task: ExportTask) => {
    setDownloading(task.id);
    try {
      const res = await exportApi.downloadUrl(task.id);
      window.open(res.url, '_blank', 'noopener,noreferrer');
    } finally {
      setDownloading(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <FileDown className="h-6 w-6" />
          导出中心
        </h1>
        <Button variant="outline" onClick={() => void load()}>
          <RefreshCcw className="mr-1 h-4 w-4" />
          刷新
        </Button>
      </div>

      <div className="grid gap-4 lg:grid-cols-[360px_minmax(0,1fr)]">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">发起导出</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-2">
              {BIZ_OPTIONS.map((option) => (
                <Button
                  key={option.value}
                  type="button"
                  variant={bizType === option.value ? 'default' : 'outline'}
                  size="sm"
                  onClick={() => setBizType(option.value)}
                >
                  {option.label}
                </Button>
              ))}
            </div>

            <div className="space-y-2">
              <div className="text-xs text-muted-foreground">业务 ID</div>
              <Input
                value={bizId}
                onChange={(e) => setBizId(e.target.value)}
                placeholder="填写客户 / 合同 / 工单 / 会话 ID"
              />
            </div>

            <Tabs value={format} onValueChange={(value) => setFormat(value as 'pdf' | 'docx')}>
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="pdf">PDF</TabsTrigger>
                <TabsTrigger value="docx">DOCX</TabsTrigger>
              </TabsList>
              <TabsContent value="pdf" />
              <TabsContent value="docx" />
            </Tabs>

            <Button className="w-full" loading={creating} onClick={() => void createTask()}>
              <Send className="h-4 w-4" />
              创建任务
            </Button>

            <div className="rounded-lg border bg-muted/30 p-3 text-xs text-muted-foreground">
              任务创建后会自动进入后台队列，完成后可直接下载。
            </div>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant={filterStatus === 'all' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setFilterStatus('all')}
            >
              全部
            </Button>
            {(['pending', 'processing', 'completed', 'failed'] as const).map((status) => (
              <Button
                key={status}
                variant={filterStatus === status ? 'default' : 'outline'}
                size="sm"
                onClick={() => setFilterStatus(status)}
              >
                {STATUS_LABEL[status]}
              </Button>
            ))}
          </div>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">我的任务</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="py-12 text-center text-sm text-muted-foreground">加载中...</div>
              ) : visibleTasks.length === 0 ? (
                <div className="py-12 text-center text-sm text-muted-foreground">暂无导出任务</div>
              ) : (
                <div className="grid gap-3">
                  {visibleTasks.map((task) => (
                    <button
                      key={task.id}
                      type="button"
                      className={`rounded-lg border p-4 text-left transition hover:bg-accent/50 ${
                        selected?.id === task.id ? 'border-primary bg-accent/30' : ''
                      }`}
                      onClick={() => setSelected(task)}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium">
                            {task.bizType} / {task.fileName || `#${task.bizId ?? '-'}`}
                          </div>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                            <span>ID {task.id}</span>
                            <span>{task.format.toUpperCase()}</span>
                            <span>{task.createTime?.slice(0, 16).replace('T', ' ')}</span>
                          </div>
                        </div>
                        <Badge variant={STATUS_VARIANT[task.status] ?? 'outline'}>
                          {STATUS_LABEL[task.status] ?? task.status}
                        </Badge>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {selected ? (
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">任务详情</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <div className="text-xs text-muted-foreground">业务类型</div>
                    <div className="font-medium">{selected.bizType}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">业务 ID</div>
                    <div className="font-medium">{selected.bizId ?? '-'}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">格式</div>
                    <div className="font-medium">{selected.format.toUpperCase()}</div>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">状态</div>
                    <div className="font-medium">{STATUS_LABEL[selected.status] ?? selected.status}</div>
                  </div>
                </div>

                {selected.errorMsg ? (
                  <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-destructive">
                    {selected.errorMsg}
                  </div>
                ) : null}

                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={selected.status !== 'completed' || downloading === selected.id}
                    onClick={() => void openDownload(selected)}
                  >
                    <Download className="mr-1 h-4 w-4" />
                    下载
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={async () => {
                      const latest = await exportApi.get(selected.id);
                      setSelected(latest);
                      await load(page);
                    }}
                  >
                    <RefreshCcw className="mr-1 h-4 w-4" />
                    刷新任务
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>

      {total > 20 ? (
        <div className="flex flex-wrap justify-center gap-2 pt-4">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
            上一页
          </Button>
          <span className="px-3 py-1 text-sm text-muted-foreground">第 {page} 页</span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= Math.ceil(total / 20)}
            onClick={() => setPage((p) => p + 1)}
          >
            下一页
          </Button>
        </div>
      ) : null}
    </div>
  );
}
