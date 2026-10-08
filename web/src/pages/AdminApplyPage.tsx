import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, Shield, XCircle } from 'lucide-react';
import { toast } from 'sonner';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Skeleton } from '@/components/ui/skeleton';
import { platformApi, type AdminApplyItem } from '@/api/platform';

export default function AdminApplyPage() {
  const [items, setItems] = useState<AdminApplyItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await platformApi.listAdminApplies(1, 50);
      setItems(res?.records ?? []);
    } catch {
      // handled
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleApprove = async (id: string) => {
    try {
      await platformApi.approveAdminApply(id);
      toast.success('宸查€氳繃');
      await load();
    } catch {
      // handled
    }
  };

  const handleReject = async (id: string) => {
    try {
      await platformApi.rejectAdminApply(id);
      toast.success('已驳回');
      await load();
    } catch {
      // handled
    }
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex h-12 shrink-0 items-center gap-2 border-b px-4">
        <Shield className="h-4 w-4" />
        <h1 className="text-sm font-semibold">管理员审核</h1>
      </div>

      <ScrollArea className="flex-1">
        <div className="app-page-narrow">
          {loading ? (
            <div className="space-y-2">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-16" />
              ))}
            </div>
          ) : items.length === 0 ? (
            <p className="text-sm text-muted-foreground">暂无待审核申请</p>
          ) : (
            items.map((item) => (
              <div
                key={item.id}
                className="app-card-row rounded-lg border p-4"
              >
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">鐢ㄦ埛 {item.userId}</span>
                    <Badge variant="outline">{item.targetRole}</Badge>
                    <Badge
                      variant={
                        item.status === 'PENDING'
                          ? 'warning'
                          : item.status === 'APPROVED'
                            ? 'success'
                            : 'destructive'
                      }
                    >
                      {item.status}
                    </Badge>
                  </div>
                  {item.reason ? (
                    <p className="mt-1 text-xs text-muted-foreground">{item.reason}</p>
                  ) : null}
                </div>
                {item.status === 'PENDING' && (
                  <div className="app-form-actions">
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-1"
                      onClick={() => handleApprove(item.id)}
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" /> 閫氳繃
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-1 text-destructive"
                      onClick={() => handleReject(item.id)}
                    >
                      <XCircle className="h-3.5 w-3.5" /> 椹冲洖
                    </Button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  );
}

