import { useEffect, useState } from 'react';
import { FileText, Plus, Search, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { contractApi, type Contract } from '@/api/contract';
import { aiEnhancementApi } from '@/api/aiEnhancement';
import { AttachmentPanel } from '@/components/attachment/AttachmentPanel';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const STATUS_MAP: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  draft: { label: '草稿', variant: 'outline' },
  pending: { label: '待审批', variant: 'secondary' },
  active: { label: '生效中', variant: 'default' },
  completed: { label: '已完成', variant: 'default' },
  terminated: { label: '已终止', variant: 'destructive' },
};

export default function ContractListPage() {
  const [list, setList] = useState<Contract[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [selected, setSelected] = useState<Contract | null>(null);
  const [form, setForm] = useState({ title: '', contractNo: '', type: 'sales', amount: '' });
  const [aiLoading, setAiLoading] = useState<string | null>(null);

  const refresh = async (p = page, kw = keyword) => {
    setLoading(true);
    try {
      const r = await contractApi.list(p, 20, undefined, kw || undefined);
      setList(r.records ?? []);
      setTotal(r.total ?? 0);
    } catch { /* ignore */ } finally { setLoading(false); }
  };

  useEffect(() => { void refresh(); }, [page]);

  const onCreate = async () => {
    if (!form.title || !form.contractNo) { toast.error('请填写合同标题和编号'); return; }
    await contractApi.create({ title: form.title, contractNo: form.contractNo, type: form.type, amount: Number(form.amount) || 0 });
    toast.success('合同已创建');
    setShowCreate(false);
    setForm({ title: '', contractNo: '', type: 'sales', amount: '' });
    void refresh();
  };

  const analyzeRisk = async (contract: Contract) => {
    setAiLoading(contract.id);
    try {
      const updated = await aiEnhancementApi.analyzeContractRisk(contract.id);
      toast.success('合同 AI 风险分析已完成');
      setSelected(updated);
      await refresh();
    } finally {
      setAiLoading(null);
    }
  };

  const generatePaymentReminders = async () => {
    setAiLoading('payments');
    try {
      const result = await aiEnhancementApi.generatePaymentReminders(7);
      toast.success(`已生成 ${result.length} 条回款提醒`);
    } finally {
      setAiLoading(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <FileText className="h-6 w-6" /> 合同管理
        </h1>
        <div className="app-page-actions">
          <Button variant="outline" disabled={aiLoading === 'payments'} onClick={generatePaymentReminders}>
            <Sparkles className="h-4 w-4 mr-1" /> 生成回款提醒
          </Button>
          <Button onClick={() => setShowCreate(true)}><Plus className="h-4 w-4 mr-1" /> 新增合同</Button>
        </div>
      </div>

      <div className="app-toolbar">
        <div className="app-search">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="搜索合同标题/编号..." className="pl-9"
            value={keyword} onChange={e => setKeyword(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && refresh(1, keyword)} />
        </div>
      </div>

      {showCreate && (
        <Card>
          <CardContent className="p-4 space-y-3">
            <Input placeholder="合同标题" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} />
            <div className="app-form-grid">
              <Input placeholder="合同编号" value={form.contractNo} onChange={e => setForm(f => ({ ...f, contractNo: e.target.value }))} />
              <Input placeholder="金额" type="number" value={form.amount} onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} />
            </div>
            <div className="app-form-actions">
              <Button onClick={onCreate}>保存</Button>
              <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            </div>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <div className="text-center py-12 text-muted-foreground">加载中...</div>
      ) : list.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">暂无合同数据</div>
      ) : (
        <div className="grid gap-3">
          {list.map(c => (
            <Card
              key={c.id}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => setSelected(c)}
            >
              <CardContent className="p-4">
                <div className="app-card-row">
                  <div className="app-card-main">
                    <div className="app-card-title-line">
                      <span className="app-card-title">{c.title}</span>
                      <Badge variant={STATUS_MAP[c.status]?.variant ?? 'outline'}>
                        {STATUS_MAP[c.status]?.label ?? c.status}
                      </Badge>
                    </div>
                    <div className="app-card-meta">
                      <span>编号: {c.contractNo}</span>
                      <span className="font-medium text-foreground">¥{c.amount?.toLocaleString()}</span>
                      {c.endDate && <span>到期: {c.endDate}</span>}
                    </div>
                  </div>
                  {c.aiRiskScore != null && (
                    <Badge variant={c.aiRiskScore > 60 ? 'destructive' : 'outline'}>
                      风险 {c.aiRiskScore}
                    </Badge>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={aiLoading === c.id}
                    onClick={(e) => {
                      e.stopPropagation();
                      void analyzeRisk(c);
                    }}
                  >
                    <Sparkles className="h-4 w-4 mr-1" /> 风险分析
                  </Button>
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
                  编号 {selected.contractNo} · 状态 {STATUS_MAP[selected.status]?.label ?? selected.status}
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-3 rounded-md border p-3 text-sm sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={aiLoading === selected.id}
                    onClick={() => void analyzeRisk(selected)}
                  >
                    <Sparkles className="h-4 w-4 mr-1" /> AI 风险分析
                  </Button>
                </div>
                <div>
                  <div className="text-muted-foreground">合同金额</div>
                  <div className="font-medium">¥{selected.amount?.toLocaleString()}</div>
                </div>
                <div>
                  <div className="text-muted-foreground">合同类型</div>
                  <div className="font-medium">{selected.type}</div>
                </div>
                <div>
                  <div className="text-muted-foreground">开始日期</div>
                  <div className="font-medium">{selected.startDate || '-'}</div>
                </div>
                <div>
                  <div className="text-muted-foreground">结束日期</div>
                  <div className="font-medium">{selected.endDate || '-'}</div>
                </div>
                {selected.aiRiskScore != null ? (
                  <div className="sm:col-span-2">
                    <div className="text-muted-foreground">AI 风险提示</div>
                    <div className="font-medium">{selected.aiRiskScore} · {selected.aiRiskNote || '暂无说明'}</div>
                  </div>
                ) : null}
              </div>
              <AttachmentPanel bizType="contract" bizId={selected.id} title="合同文件" />
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
