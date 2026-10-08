import { useEffect, useState } from 'react';
import { AlertTriangle, BarChart3, BriefcaseBusiness, ClipboardList, FileText, Send, Users } from 'lucide-react';

import { aiEnhancementApi } from '@/api/aiEnhancement';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

type RiskBlock = Record<string, unknown>;

const BLOCKS = [
  { key: 'customerRisk', title: '客户风险', icon: Users, endpoint: 'customerRisk' as const },
  { key: 'contractRisk', title: '合同回款风险', icon: FileText, endpoint: 'contractRisk' as const },
  { key: 'workOrderRisk', title: '工单风险', icon: ClipboardList, endpoint: 'workOrderRisk' as const },
  { key: 'dispatchRisk', title: '派发任务风险', icon: Send, endpoint: 'dispatchRisk' as const },
];

export default function AiRiskDashboardPage() {
  const [overview, setOverview] = useState<Record<string, RiskBlock>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      try {
        const data = await aiEnhancementApi.riskOverview();
        setOverview(data ?? {});
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
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} className="h-40" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <BarChart3 className="h-6 w-6" />
          AI 风险看板
        </h1>
      </div>

      <Card className="border-primary/20 bg-primary/5">
        <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <AlertTriangle className="h-7 w-7 shrink-0 text-primary" />
          <div className="min-w-0 flex-1">
            <div className="font-medium">全业务风险聚合</div>
            <div className="text-sm text-muted-foreground">
              AI 汇总客户、合同回款、工单和派发任务风险，帮助管理层快速定位异常。
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        {BLOCKS.map((block) => {
          const Icon = block.icon;
          const data = overview[block.key] ?? {};
          return (
            <Card key={block.key}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="flex items-center gap-2 text-base">
                  <Icon className="h-5 w-5" />
                  {block.title}
                </CardTitle>
                <Badge variant={String(data.warning ?? '').includes('风险') || String(data.warning ?? '').includes('超时') || String(data.warning ?? '').includes('逾期') ? 'destructive' : 'outline'}>
                  {String(data.warning ?? '暂无结论')}
                </Badge>
              </CardHeader>
              <CardContent>
                <div className="grid gap-3 sm:grid-cols-2">
                  {Object.entries(data)
                    .filter(([key]) => key !== 'warning')
                    .map(([key, value]) => (
                      <div key={key} className="rounded-md border p-3">
                        <div className="text-xs text-muted-foreground">{label(key)}</div>
                        <div className="mt-1 text-lg font-semibold">{formatValue(value)}</div>
                      </div>
                    ))}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <BriefcaseBusiness className="h-5 w-5" />
            管理建议
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2 text-sm text-muted-foreground">
          <p>优先处理高风险合同、逾期回款和超时工单。</p>
          <p>对沉睡客户触发客户 AI 评分和下一步跟进建议。</p>
          <p>对即将到期合同生成智能日程与回款提醒，降低遗漏风险。</p>
        </CardContent>
      </Card>
    </div>
  );
}

function label(key: string) {
  const map: Record<string, string> = {
    total: '总数',
    highValueCount: '高价值客户',
    dormantOrLostCount: '沉睡/流失客户',
    unscoredCount: '未评分客户',
    totalContracts: '合同总数',
    highRiskContracts: '高风险合同',
    endingSoonContracts: '即将到期合同',
    pendingPayments: '待回款',
    overduePayments: '逾期回款',
    totalWorkOrders: '工单总数',
    urgentCount: '紧急数量',
    overdueCount: '超时/逾期数量',
    unassignedCount: '未指派数量',
    totalDispatches: '派发总数',
  };
  return map[key] ?? key;
}

function formatValue(value: unknown) {
  if (value == null) return '-';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
