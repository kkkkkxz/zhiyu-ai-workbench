import { useEffect, useState } from 'react';
import { ClipboardList, Plus, Search, Sparkles, UserCheck } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { workOrderApi, type WorkOrder } from '@/api/workorder';
import { aiEnhancementApi } from '@/api/aiEnhancement';
import { AttachmentPanel } from '@/components/attachment/AttachmentPanel';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const PRIORITY_COLOR: Record<string, string> = {
  low: 'bg-slate-100 text-slate-700',
  medium: 'bg-blue-100 text-blue-700',
  high: 'bg-orange-100 text-orange-700',
  urgent: 'bg-red-100 text-red-700',
};

const STATUS_MAP: Record<string, string> = {
  open: '待处理', assigned: '已指派', in_progress: '处理中', resolved: '已解决', closed: '已关闭',
};

export default function WorkOrderListPage() {
  const [list, setList] = useState<WorkOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [selected, setSelected] = useState<WorkOrder | null>(null);
  const [form, setForm] = useState({ title: '', type: 'support', priority: 'medium', description: '' });
  const [aiLoading, setAiLoading] = useState<string | null>(null);

  const refresh = async (p = page) => {
    setLoading(true);
    try {
      const r = await workOrderApi.list(p, 20, undefined, undefined, keyword || undefined);
      setList(r.records ?? []);
      setTotal(r.total ?? 0);
    } catch { /* ignore */ } finally { setLoading(false); }
  };

  useEffect(() => { void refresh(); }, [page]);

  const onCreate = async () => {
    if (!form.title) { toast.error('请填写工单标题'); return; }
    await workOrderApi.create(form);
    toast.success('工单已创建');
    setShowCreate(false);
    setForm({ title: '', type: 'support', priority: 'medium', description: '' });
    void refresh();
  };

  const analyzeOrder = async (order: WorkOrder) => {
    setAiLoading(`analyze-${order.id}`);
    try {
      const updated = await aiEnhancementApi.analyzeWorkOrder(order.id);
      toast.success('AI 处理建议已生成');
      setSelected(updated);
      await refresh();
    } finally {
      setAiLoading(null);
    }
  };

  const smartAssign = async (order: WorkOrder) => {
    setAiLoading(`assign-${order.id}`);
    try {
      const updated = await aiEnhancementApi.smartAssignWorkOrder(order.id);
      toast.success('AI 智能分配已完成');
      setSelected(updated);
      await refresh();
    } finally {
      setAiLoading(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <ClipboardList className="h-6 w-6" /> 工单管理
        </h1>
        <div className="app-page-actions">
          <Button onClick={() => setShowCreate(true)}><Plus className="h-4 w-4 mr-1" /> 新建工单</Button>
        </div>
      </div>

      <div className="app-toolbar">
        <div className="app-search">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="搜索工单..." className="pl-9"
            value={keyword} onChange={e => setKeyword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && refresh(1)} />
        </div>
      </div>

      {showCreate && (
        <Card>
          <CardContent className="p-4 space-y-3">
            <Input placeholder="工单标题" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} />
            <textarea className="w-full min-h-[80px] rounded-md border border-input bg-background px-3 py-2 text-sm"
              placeholder="问题描述" value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
            <div className="app-form-actions">
              <select className="h-10 w-full rounded-md border px-3 py-2 text-sm sm:w-auto" value={form.priority}
                onChange={e => setForm(f => ({ ...f, priority: e.target.value }))}>
                <option value="low">低</option><option value="medium">中</option>
                <option value="high">高</option><option value="urgent">紧急</option>
              </select>
              <Button onClick={onCreate}>提交</Button>
              <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <div className="text-center py-12 text-muted-foreground">加载中...</div>
      ) : list.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">暂无工单</div>
      ) : (
        <div className="grid gap-3">
          {list.map(o => (
            <Card
              key={o.id}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => setSelected(o)}
            >
              <CardContent className="p-4">
                <div className="app-card-row">
                  <div className="app-card-main">
                    <div className="app-card-title-line">
                      <span className="text-xs text-muted-foreground font-mono">{o.orderNo}</span>
                      <span className="app-card-title">{o.title}</span>
                      <span className={`text-xs px-2 py-0.5 rounded-full ${PRIORITY_COLOR[o.priority] ?? ''}`}>
                        {o.priority}
                      </span>
                    </div>
                    <div className="app-card-meta">
                      <Badge variant="outline">{STATUS_MAP[o.status] ?? o.status}</Badge>
                      {o.aiCategory && <span>AI分类: {o.aiCategory}</span>}
                      <span>{o.createTime?.slice(0, 16).replace('T', ' ')}</span>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={aiLoading === `analyze-${o.id}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        void analyzeOrder(o);
                      }}
                    >
                      <Sparkles className="h-4 w-4 mr-1" /> 分析
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={aiLoading === `assign-${o.id}`}
                      onClick={(e) => {
                        e.stopPropagation();
                        void smartAssign(o);
                      }}
                    >
                      <UserCheck className="h-4 w-4 mr-1" /> 分配
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={Boolean(selected)} onOpenChange={(open) => !open && setSelected(null)}>
        <DialogContent className="max-h-[88vh] overflow-y-auto sm:max-w-3xl">
          {selected ? (
            <>
              <DialogHeader>
                <DialogTitle>{selected.title}</DialogTitle>
                <DialogDescription>
                  {selected.orderNo} · {STATUS_MAP[selected.status] ?? selected.status}
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-3 rounded-md border p-3 text-sm sm:grid-cols-2">
                <div className="flex flex-wrap gap-2 sm:col-span-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={aiLoading === `analyze-${selected.id}`}
                    onClick={() => void analyzeOrder(selected)}
                  >
                    <Sparkles className="h-4 w-4 mr-1" /> AI 处理建议
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={aiLoading === `assign-${selected.id}`}
                    onClick={() => void smartAssign(selected)}
                  >
                    <UserCheck className="h-4 w-4 mr-1" /> AI 智能分配
                  </Button>
                </div>
                <div>
                  <div className="text-muted-foreground">优先级</div>
                  <div className="font-medium">{selected.priority}</div>
                </div>
                <div>
                  <div className="text-muted-foreground">类型</div>
                  <div className="font-medium">{selected.type}</div>
                </div>
                <div className="sm:col-span-2">
                  <div className="text-muted-foreground">问题描述</div>
                  <div className="whitespace-pre-wrap font-medium">{selected.description || '-'}</div>
                </div>
                {selected.aiSuggestion ? (
                  <div className="sm:col-span-2">
                    <div className="text-muted-foreground">AI 处理建议</div>
                    <div className="whitespace-pre-wrap font-medium">{selected.aiSuggestion}</div>
                  </div>
                ) : null}
                {selected.aiAssignedReason ? (
                  <div className="sm:col-span-2">
                    <div className="text-muted-foreground">AI 分配理由</div>
                    <div className="whitespace-pre-wrap font-medium">{selected.aiAssignedReason}</div>
                  </div>
                ) : null}
              </div>
              <AttachmentPanel bizType="work_order" bizId={selected.id} title="工单附件" />
            </>
          ) : null}
        </DialogContent>
      </Dialog>

      {total > 20 && (
        <div className="flex flex-wrap justify-center gap-2 pt-4">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(p => p - 1)}>上一页</Button>
          <span className="px-3 py-1 text-sm text-muted-foreground">第 {page} 页</span>
          <Button variant="outline" size="sm" disabled={page >= Math.ceil(total / 20)} onClick={() => setPage(p => p + 1)}>下一页</Button>
        </div>
      )}
    </div>
  );
}
