import { useEffect, useState } from 'react';
import { GitBranch, CheckCircle, XCircle, Clock, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { workflowApi, type WfTask, type WfInstance } from '@/api/workflow';
import { aiEnhancementApi, type ApprovalSuggestion } from '@/api/aiEnhancement';

const STATUS_MAP: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  running: { label: '审批中', variant: 'secondary' },
  approved: { label: '已通过', variant: 'default' },
  rejected: { label: '已驳回', variant: 'destructive' },
  canceled: { label: '已取消', variant: 'outline' },
};

export default function WorkflowPage() {
  const [tab, setTab] = useState('tasks');
  const [tasks, setTasks] = useState<WfTask[]>([]);
  const [instances, setInstances] = useState<WfInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [comment, setComment] = useState('');
  const [suggestions, setSuggestions] = useState<Record<string, ApprovalSuggestion>>({});
  const [aiLoading, setAiLoading] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const [t, i] = await Promise.all([workflowApi.myTasks(), workflowApi.myInitiated()]);
      setTasks(t.records ?? []);
      setInstances(i.records ?? []);
    } catch { /* ignore */ } finally { setLoading(false); }
  };

  useEffect(() => { void refresh(); }, []);

  const handleAction = async (taskId: string, action: 'approve' | 'reject') => {
    if (action === 'approve') {
      await workflowApi.approve(taskId, comment);
      toast.success('已通过');
    } else {
      await workflowApi.reject(taskId, comment);
      toast.success('已驳回');
    }
    setComment('');
    void refresh();
  };

  const loadSuggestion = async (taskId: string) => {
    setAiLoading(taskId);
    try {
      const suggestion = await aiEnhancementApi.approvalSuggestion(taskId);
      setSuggestions(prev => ({ ...prev, [taskId]: suggestion }));
      toast.success('AI 审批建议已生成');
    } finally {
      setAiLoading(null);
    }
  };

  const loadBottleneckReport = async () => {
    setAiLoading('bottleneck');
    try {
      const report = await aiEnhancementApi.workflowBottleneckReport();
      toast.success(String(report.summary ?? '审批瓶颈分析已生成'));
    } finally {
      setAiLoading(null);
    }
  };

  return (
    <div className="app-page">
      <h1 className="app-page-title">
        <GitBranch className="h-6 w-6" /> 审批管理
      </h1>
      <div className="app-page-actions">
        <Button variant="outline" disabled={aiLoading === 'bottleneck'} onClick={() => void loadBottleneckReport()}>
          <Sparkles className="h-4 w-4 mr-1" /> AI 瓶颈分析
        </Button>
      </div>

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="grid w-full grid-cols-2 sm:w-auto">
          <TabsTrigger value="tasks" className="gap-1">
            <Clock className="h-4 w-4" /> 我的待办 {tasks.length > 0 && `(${tasks.length})`}
          </TabsTrigger>
          <TabsTrigger value="initiated" className="gap-1">
            <GitBranch className="h-4 w-4" /> 我发起的
          </TabsTrigger>
        </TabsList>

        <TabsContent value="tasks">
          {loading ? <div className="text-center py-8">加载中...</div> : tasks.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">没有待办审批</div>
          ) : (
            <div className="grid gap-3">
              {tasks.map(t => (
                <Card key={t.id}>
                  <CardContent className="p-4 space-y-3">
                    <div className="app-card-row">
                      <div>
                        <div className="font-semibold">{t.nodeName ?? t.nodeCode}</div>
                        <div className="text-sm text-muted-foreground">{t.createTime?.slice(0, 16).replace('T', ' ')}</div>
                      </div>
                      <Badge variant="outline">待审批</Badge>
                    </div>
                    <div className="app-toolbar">
                      <Input placeholder="审批意见（可选）" value={comment} onChange={e => setComment(e.target.value)} className="flex-1" />
                      <Button size="sm" variant="outline" disabled={aiLoading === t.id} onClick={() => void loadSuggestion(t.id)}>
                        <Sparkles className="h-4 w-4 mr-1" /> AI建议
                      </Button>
                      <Button size="sm" onClick={() => handleAction(t.id, 'approve')}>
                        <CheckCircle className="h-4 w-4 mr-1" /> 通过
                      </Button>
                      <Button size="sm" variant="destructive" onClick={() => handleAction(t.id, 'reject')}>
                        <XCircle className="h-4 w-4 mr-1" /> 驳回
                      </Button>
                    </div>
                    {suggestions[t.id] ? (
                      <div className="rounded-md bg-primary/5 p-3 text-sm">
                        <div className="font-medium">AI 审批建议 · 风险 {suggestions[t.id].riskLevel}</div>
                        <div className="mt-1 text-muted-foreground">{suggestions[t.id].suggestion}</div>
                      </div>
                    ) : null}
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="initiated">
          {loading ? <div className="text-center py-8">加载中...</div> : instances.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground">暂未发起审批</div>
          ) : (
            <div className="grid gap-3">
              {instances.map(i => (
                <Card key={i.id}>
                  <CardContent className="p-4">
                    <div className="app-card-row">
                      <div>
                        <div className="font-semibold">{i.title}</div>
                        <div className="text-sm text-muted-foreground">
                          {i.bizType} · {i.createTime?.slice(0, 16).replace('T', ' ')}
                          {i.currentNode && <span> · 当前节点: {i.currentNode}</span>}
                        </div>
                      </div>
                      <Badge variant={STATUS_MAP[i.status]?.variant ?? 'outline'}>
                        {STATUS_MAP[i.status]?.label ?? i.status}
                      </Badge>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}

