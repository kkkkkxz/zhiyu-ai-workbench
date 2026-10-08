import { useState } from 'react';
import { Check, X, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { DataTable } from './DataTable';
import { aiBizActionApi, type BizActionResult, type BizActionType } from '@/api/aiBizAction';

interface BizActionCardProps {
  result: BizActionResult;
  onDone?: (executed: BizActionResult) => void;
}

export function BizActionCard({ result, onDone }: BizActionCardProps) {
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [doneResult, setDoneResult] = useState<BizActionResult | null>(null);

  // 查询结果直接展示
  if (result.queryResult) {
    return (
      <div className="my-2 space-y-2">
        {result.summary && (
          <div className="flex items-center gap-2 text-sm font-medium">
            <Sparkles className="h-4 w-4 text-primary" />
            {result.summary}
          </div>
        )}
        {result.columns && result.rows && result.rows.length > 0 && (
          <DataTable columns={result.columns} rows={result.rows} />
        )}
      </div>
    );
  }

  // 已执行
  if (result.executed || done) {
    return (
      <Card className="border-green-200 bg-green-50/50 my-2">
        <CardContent className="p-4 flex items-center gap-2 text-green-700">
          <Check className="h-5 w-5" />
          <span className="font-medium">{doneResult?.successMessage ?? result.successMessage ?? '操作成功'}</span>
        </CardContent>
      </Card>
    );
  }

  // 需要确认
  if (result.needConfirm) {
    const handleConfirm = async () => {
      setLoading(true);
      try {
        const r = await aiBizActionApi.confirm(
          result.actionType as BizActionType,
          result.previewData ?? {},
        );
        setDoneResult(r);
        setDone(true);
        toast.success(r.successMessage ?? '操作成功');
        onDone?.(r);
      } catch (e: any) {
        toast.error(e?.response?.data?.message || e?.message || '操作失败');
      } finally {
        setLoading(false);
      }
    };

    return (
      <Card className="border-amber-200 bg-amber-50/30 my-2">
        <CardContent className="p-4 space-y-3">
          <div className="text-sm whitespace-pre-wrap">{result.confirmMessage}</div>
          <div className="flex gap-2">
            <Button size="sm" onClick={handleConfirm} disabled={loading}>
              <Check className="h-4 w-4 mr-1" />
              {loading ? '执行中...' : '确认执行'}
            </Button>
            <Button size="sm" variant="outline" disabled={loading}
              onClick={() => { setDone(true); setDoneResult({ ...result, successMessage: '已取消操作', executed: true, actionType: result.actionType }); }}>
              <X className="h-4 w-4 mr-1" /> 取消
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  // 错误或未知
  if (result.summary) {
    return (
      <div className="my-2 text-sm text-muted-foreground">{result.summary}</div>
    );
  }

  return null;
}
