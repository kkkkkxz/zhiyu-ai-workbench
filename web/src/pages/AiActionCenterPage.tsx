import { useEffect, useState } from 'react';
import { Activity, CheckCircle2, Clock3, XCircle } from 'lucide-react';

import { aiActionLogApi, type AiActionExecutionLog } from '@/api/aiActionLog';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';

export default function AiActionCenterPage() {
  const [list, setList] = useState<AiActionExecutionLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  const refresh = async (nextPage = page) => {
    setLoading(true);
    try {
      const result = await aiActionLogApi.list(nextPage, 20);
      setList(result.records ?? []);
      setTotal(result.total ?? 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh(page);
  }, [page]);

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Activity className="h-6 w-6" />
          AI 动作中心
        </h1>
      </div>

      <Card className="border-primary/20 bg-primary/5">
        <CardContent className="p-4 text-sm text-muted-foreground">
          AI 在业务系统里的每一次预览和执行都会记录在这里。后续高风险动作、失败重试、待确认动作都会继续收敛到这个中心。
        </CardContent>
      </Card>

      {loading ? (
        <div className="py-12 text-center text-muted-foreground">正在加载 AI 动作...</div>
      ) : list.length === 0 ? (
        <div className="py-12 text-center text-muted-foreground">暂无 AI 动作记录</div>
      ) : (
        <div className="grid gap-3">
          {list.map((item) => (
            <Card key={item.id}>
              <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted">
                  {item.status === 'SUCCESS' ? (
                    <CheckCircle2 className="h-5 w-5 text-green-600" />
                  ) : (
                    <XCircle className="h-5 w-5 text-destructive" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{item.actionName || item.actionCode}</span>
                    <Badge variant={item.phase === 'EXECUTE' ? 'default' : 'secondary'}>
                      {item.phase === 'EXECUTE' ? '执行' : '预览'}
                    </Badge>
                    <Badge variant={item.status === 'SUCCESS' ? 'outline' : 'destructive'}>
                      {item.status === 'SUCCESS' ? '成功' : '失败'}
                    </Badge>
                  </div>
                  {item.userInput ? (
                    <div className="mt-1 truncate text-sm text-muted-foreground">{item.userInput}</div>
                  ) : null}
                  {item.errorMessage ? (
                    <div className="mt-1 text-sm text-destructive">{item.errorMessage}</div>
                  ) : null}
                </div>
                <div className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
                  <Clock3 className="h-3.5 w-3.5" />
                  <span>{item.createTime?.slice(0, 16).replace('T', ' ')}</span>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

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
