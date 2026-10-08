import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Mail, Phone, Plus, Search, Sparkles, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { crmApi, type Customer } from '@/api/crm';
import { aiEnhancementApi } from '@/api/aiEnhancement';

const STATUS_MAP: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  potential: { label: '潜在', variant: 'outline' },
  active: { label: '活跃', variant: 'default' },
  inactive: { label: '不活跃', variant: 'secondary' },
  lost: { label: '已流失', variant: 'destructive' },
};

export default function CustomerListPage() {
  const navigate = useNavigate();
  const [list, setList] = useState<Customer[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [deleting, setDeleting] = useState<Customer | null>(null);
  const [scoring, setScoring] = useState<string | null>(null);

  const refresh = async (nextPage = page, nextKeyword = keyword) => {
    setLoading(true);
    try {
      const result = await crmApi.listCustomers(nextPage, 20, undefined, nextKeyword || undefined);
      setList(result.records ?? []);
      setTotal(result.total ?? 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, [page]);

  const scoreCustomer = async (customer: Customer) => {
    setScoring(customer.id);
    try {
      await aiEnhancementApi.scoreCustomer(customer.id);
      toast.success('AI 评分已更新');
      await refresh();
    } finally {
      setScoring(null);
    }
  };

  const batchScore = async () => {
    setScoring('batch');
    try {
      const result = await aiEnhancementApi.batchScoreCustomers();
      toast.success(`已完成 ${result.length} 个客户评分`);
      await refresh();
    } finally {
      setScoring(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Building2 className="h-6 w-6" />
          客户管理
        </h1>
        <div className="app-page-actions">
          <Button variant="outline" disabled={scoring === 'batch'} onClick={batchScore}>
            <Sparkles className="mr-1 h-4 w-4" />
            AI 批量评分
          </Button>
          <Button onClick={() => navigate('/crm/customers/new')}>
            <Plus className="mr-1 h-4 w-4" />
            新增客户
          </Button>
        </div>
      </div>

      <div className="app-toolbar">
        <div className="app-search">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="搜索客户名称/电话..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                setPage(1);
                void refresh(1, keyword);
              }
            }}
          />
        </div>
        <Button variant="outline" onClick={() => {
          setPage(1);
          void refresh(1, keyword);
        }}>
          搜索
        </Button>
      </div>

      {loading ? (
        <div className="py-12 text-center text-sm text-muted-foreground">加载中...</div>
      ) : list.length === 0 ? (
        <div className="py-12 text-center text-sm text-muted-foreground">暂无客户数据</div>
      ) : (
        <div className="grid gap-3">
          {list.map((customer) => (
            <Card
              key={customer.id}
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => navigate(`/crm/customers/${customer.id}`)}
            >
              <CardContent className="p-4">
                <div className="app-card-row">
                  <div className="app-card-main">
                    <div className="app-card-title-line">
                      <span className="app-card-title">{customer.name}</span>
                      {customer.shortName ? (
                        <span className="text-sm text-muted-foreground">({customer.shortName})</span>
                      ) : null}
                      <Badge variant={STATUS_MAP[customer.status]?.variant ?? 'outline'}>
                        {STATUS_MAP[customer.status]?.label ?? customer.status}
                      </Badge>
                      {customer.grade ? (
                        <Badge variant={customer.aiScore && customer.aiScore >= 75 ? 'default' : 'outline'}>
                          {customer.grade} · {customer.aiScore ?? '-'}分
                        </Badge>
                      ) : null}
                    </div>
                    <div className="app-card-meta">
                      {customer.industry ? <span>{customer.industry}</span> : null}
                      {customer.phone ? (
                        <span className="flex items-center gap-1"><Phone className="h-3 w-3" />{customer.phone}</span>
                      ) : null}
                      {customer.email ? (
                        <span className="flex items-center gap-1"><Mail className="h-3 w-3" />{customer.email}</span>
                      ) : null}
                      {customer.lifecycleStage ? <span>生命周期: {customer.lifecycleStage}</span> : null}
                      {customer.aiNextAction ? <span>AI建议: {customer.aiNextAction}</span> : null}
                    </div>
                  </div>
                  <Button
                    className="self-start sm:self-center"
                    variant="outline"
                    size="sm"
                    disabled={scoring === customer.id}
                    onClick={(e) => {
                      e.stopPropagation();
                      void scoreCustomer(customer);
                    }}
                  >
                    <Sparkles className="mr-1 h-4 w-4" />
                    评分
                  </Button>
                  <Button
                    className="self-start sm:self-center"
                    variant="ghost"
                    size="icon"
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeleting(customer);
                    }}
                  >
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {total > 20 ? (
        <div className="flex flex-wrap justify-center gap-2 pt-4">
          <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
            上一页
          </Button>
          <span className="px-3 py-1 text-sm text-muted-foreground">
            第 {page} 页 / 共 {Math.ceil(total / 20)} 页
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= Math.ceil(total / 20)}
            onClick={() => setPage((value) => value + 1)}
          >
            下一页
          </Button>
        </div>
      ) : null}

      <ConfirmDialog
        open={!!deleting}
        title="删除客户"
        description={`确定要删除“${deleting?.name ?? ''}”吗？`}
        destructive
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
        onConfirm={async () => {
          if (!deleting) return;
          await crmApi.deleteCustomer(deleting.id);
          toast.success('已删除');
          setDeleting(null);
          void refresh();
        }}
      />
    </div>
  );
}
