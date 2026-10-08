import { useEffect, useState } from 'react';
import { Building2, Database, FileText, MessageSquare, Shield, Users } from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { platformApi, type PlatformOverview, type TenantRanking } from '@/api/platform';

export default function PlatformDashboardPage() {
  const [overview, setOverview] = useState<PlatformOverview | null>(null);
  const [ranking, setRanking] = useState<TenantRanking[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const [o, r] = await Promise.all([
          platformApi.overview(),
          platformApi.tenantRanking(10),
        ]);
        setOverview(o);
        setRanking(r ?? []);
      } catch {
        // handled by interceptor
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  if (loading) {
    return (
      <div className="app-page">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      </div>
    );
  }

  const stats = overview
    ? [
        { label: '租户总数', value: overview.tenantTotal, sub: `活跃 ${overview.activeTenantTotal}`, icon: Building2 },
        { label: '用户总数', value: overview.userTotal, sub: `今日活跃 ${overview.todayActiveUserTotal}`, icon: Users },
        { label: '今日对话', value: overview.todayChatTotal, sub: `Token ${overview.todayTokenTotal}`, icon: MessageSquare },
        { label: '知识库', value: overview.kbTotal, sub: `文档 ${overview.documentTotal}`, icon: Database },
        { label: '部门', value: overview.departmentTotal, sub: '', icon: FileText },
        { label: '待审核', value: overview.pendingAdminApplyTotal, sub: '', icon: Shield },
      ]
    : [];

  return (
    <div className="app-page">
      <h1 className="app-page-title">平台数据看板</h1>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {stats.map((s) => {
          const Icon = s.icon;
          return (
            <Card key={s.label}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium text-muted-foreground">
                  {s.label}
                </CardTitle>
                <Icon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{s.value}</div>
                {s.sub ? (
                  <p className="mt-1 text-xs text-muted-foreground">{s.sub}</p>
                ) : null}
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold">租户排名</h2>
        {ranking.length === 0 ? (
          <p className="text-sm text-muted-foreground">暂无数据</p>
        ) : (
          <div className="overflow-x-auto rounded-lg border">
            <table className="min-w-[640px] w-full text-sm">
              <thead className="border-b bg-muted/50">
                <tr>
                  <th className="px-4 py-2 text-left font-medium">租户</th>
                  <th className="px-4 py-2 text-left font-medium">编码</th>
                  <th className="px-4 py-2 text-right font-medium">用户</th>
                  <th className="px-4 py-2 text-right font-medium">状态</th>
                </tr>
              </thead>
              <tbody>
                {ranking.map((t) => (
                  <tr key={t.tenantId} className="border-b last:border-0">
                    <td className="px-4 py-2 font-medium">{t.tenantName}</td>
                    <td className="px-4 py-2 text-muted-foreground">{t.tenantCode}</td>
                    <td className="px-4 py-2 text-right">{t.userTotal}</td>
                    <td className="px-4 py-2 text-right">
                      <Badge variant={t.status === 1 ? 'default' : 'secondary'}>
                        {t.status === 1 ? '启用' : '停用'}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

