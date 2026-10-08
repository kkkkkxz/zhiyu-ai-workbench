import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Building2,
  CalendarDays,
  ClipboardList,
  FileDown,
  FileText,
  GitBranch,
  MessageSquare,
  Send,
  Sparkles,
} from 'lucide-react';

import { notificationApi } from '@/api/notification';
import { dispatchApi } from '@/api/dispatch';
import { workflowApi } from '@/api/workflow';
import { scheduleApi } from '@/api/schedule';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

interface StatCard {
  label: string;
  value: number | string;
  icon: React.ElementType;
  color: string;
  to: string;
}

interface QuickLink {
  label: string;
  icon: React.ElementType;
  to: string;
}

const aiLinks: QuickLink[] = [
  { label: 'AI 对话', icon: MessageSquare, to: '/chat' },
  { label: '知识库', icon: Sparkles, to: '/kb' },
  { label: 'AI 动作中心', icon: Sparkles, to: '/ai-actions' },
  { label: 'AI 风险看板', icon: Sparkles, to: '/ai-risk-dashboard' },
];

const businessLinks: QuickLink[] = [
  { label: '客户管理', icon: Building2, to: '/crm/customers' },
  { label: '合同管理', icon: FileText, to: '/contracts' },
  { label: '工单管理', icon: ClipboardList, to: '/work-orders' },
  { label: '工作派发', icon: Send, to: '/dispatch' },
  { label: '日程管理', icon: CalendarDays, to: '/schedule' },
  { label: '审批流程', icon: GitBranch, to: '/workflow' },
  { label: '导出中心', icon: FileDown, to: '/exports' },
];

export default function WorkbenchPage() {
  const navigate = useNavigate();
  const [stats, setStats] = useState({
    unreadNotifications: 0,
    pendingApprovals: 0,
    todaySchedules: 0,
    receivedDispatches: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        const [notif, tasks, schedules, dispatches] = await Promise.all([
          notificationApi.unreadCount(),
          workflowApi.myTasks(1, 1),
          scheduleApi.list(1, 100),
          dispatchApi.myReceived(1, 1),
        ]);
        setStats({
          unreadNotifications: notif.count ?? 0,
          pendingApprovals: tasks.total ?? 0,
          todaySchedules: schedules.total ?? 0,
          receivedDispatches: dispatches.total ?? 0,
        });
      } catch {
        // ignore
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  const cards: StatCard[] = [
    { label: '未读通知', value: stats.unreadNotifications, icon: Bell, color: 'text-blue-600', to: '/notifications' },
    { label: '待办审批', value: stats.pendingApprovals, icon: GitBranch, color: 'text-orange-600', to: '/workflow' },
    { label: '今日日程', value: stats.todaySchedules, icon: CalendarDays, color: 'text-green-600', to: '/schedule' },
    { label: '收到派发', value: stats.receivedDispatches, icon: Send, color: 'text-purple-600', to: '/dispatch' },
  ];

  const renderLinks = (links: QuickLink[]) => (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 xl:grid-cols-2">
      {links.map((link) => (
        <Button
          key={link.label}
          variant="ghost"
          className="h-auto min-h-20 flex-col gap-2 px-3 py-3"
          onClick={() => navigate(link.to)}
        >
          <link.icon className="h-5 w-5 text-muted-foreground" />
          <span className="text-xs leading-tight">{link.label}</span>
        </Button>
      ))}
    </div>
  );

  return (
    <div className="app-page">
      <div className="app-page-header">
        <div className="min-w-0 space-y-1">
          <h1 className="app-page-title">工作台</h1>
          <p className="text-sm text-muted-foreground">常用入口、今日概览和 AI 助手放在同一屏。</p>
        </div>
        <div className="app-page-actions">
          <Button variant="outline" onClick={() => navigate('/chat')}>
            <MessageSquare className="mr-2 h-4 w-4" />
            AI 对话
          </Button>
          <Button onClick={() => navigate('/work-orders')}>
            <ClipboardList className="mr-2 h-4 w-4" />
            查看工单
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 min-[420px]:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => (
          <Card
            key={card.label}
            className="cursor-pointer transition-shadow hover:shadow-md"
            onClick={() => navigate(card.to)}
          >
            <CardContent className="flex items-center gap-3 p-4">
              <div className={`rounded-lg bg-muted p-2 ${card.color}`}>
                <card.icon className="h-5 w-5" />
              </div>
              <div>
                <div className="text-2xl font-bold">{loading ? '-' : card.value}</div>
                <div className="text-xs text-muted-foreground">{card.label}</div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-semibold">快捷入口</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 lg:grid-cols-2">
            <section className="space-y-3">
              <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                AI 功能
              </div>
              {renderLinks(aiLinks)}
            </section>
            <section className="space-y-3">
              <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                业务功能
              </div>
              {renderLinks(businessLinks)}
            </section>
          </CardContent>
        </Card>

        <Card className="border-primary/20 bg-primary/5">
          <CardContent className="flex h-full flex-col gap-4 p-4">
            <div className="flex items-start gap-3">
              <MessageSquare className="mt-0.5 h-8 w-8 shrink-0 text-primary" />
              <div className="min-w-0 flex-1 space-y-1">
                <div className="font-medium">AI 智能助手</div>
                <div className="text-sm leading-6 text-muted-foreground">
                  试试问我“本月新增了多少客户？”或者“帮我创建一个明天的会议日程”。
                </div>
              </div>
            </div>
            <div className="mt-auto flex flex-col gap-2 sm:flex-row">
              <Button className="w-full sm:w-auto" onClick={() => navigate('/chat')}>
                开始对话
              </Button>
              <Button variant="outline" className="w-full sm:w-auto" onClick={() => navigate('/kb')}>
                查看知识库
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
