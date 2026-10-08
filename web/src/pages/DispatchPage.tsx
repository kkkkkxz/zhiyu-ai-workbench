import { useEffect, useState } from 'react';
import { Send, Inbox, Plus, CheckCircle2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { dispatchApi, type Dispatch, type DispatchDeptOption, type DispatchUserOption } from '@/api/dispatch';

const STATUS_MAP: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  pending: { label: '待接收', variant: 'outline' },
  accepted: { label: '已接收', variant: 'secondary' },
  in_progress: { label: '进行中', variant: 'default' },
  completed: { label: '已完成', variant: 'default' },
  rejected: { label: '已拒绝', variant: 'destructive' },
};

export default function DispatchPage() {
  const [tab, setTab] = useState('received');
  const [sentList, setSentList] = useState<Dispatch[]>([]);
  const [receivedList, setReceivedList] = useState<Dispatch[]>([]);
  const [departments, setDepartments] = useState<DispatchDeptOption[]>([]);
  const [users, setUsers] = useState<DispatchUserOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', deptId: '', assigneeId: '' });

  const refresh = async () => {
    setLoading(true);
    try {
      const [s, r] = await Promise.all([dispatchApi.mySent(), dispatchApi.myReceived()]);
      setSentList(s.records ?? []);
      setReceivedList(r.records ?? []);
    } catch { /* ignore */ } finally { setLoading(false); }
  };

  useEffect(() => { void refresh(); }, []);

  useEffect(() => {
    if (!showCreate) return;
    dispatchApi.assignableDepartments()
      .then(rows => {
        setDepartments(rows);
        const firstDeptId = rows[0]?.id ?? '';
        setForm(f => ({ ...f, deptId: f.deptId || firstDeptId, assigneeId: '' }));
      })
      .catch(() => toast.error('加载可指派部门失败'));
  }, [showCreate]);

  useEffect(() => {
    if (!showCreate || !form.deptId) {
      setUsers([]);
      return;
    }
    dispatchApi.assignableUsers(form.deptId)
      .then(rows => {
        setUsers(rows);
        setForm(f => ({ ...f, assigneeId: rows.some(u => u.id === f.assigneeId) ? f.assigneeId : '' }));
      })
      .catch(() => toast.error('加载可指派人员失败'));
  }, [showCreate, form.deptId]);

  const onCreate = async () => {
    if (!form.title) { toast.error('请填写标题'); return; }
    if (!form.deptId) { toast.error('请选择部门'); return; }
    if (!form.assigneeId) { toast.error('请选择指派人员'); return; }
    await dispatchApi.create({ title: form.title, description: form.description, assigneeIds: [form.assigneeId] });
    toast.success('派发成功');
    setShowCreate(false);
    setForm({ title: '', description: '', deptId: '', assigneeId: '' });
    void refresh();
  };

  const renderList = (items: Dispatch[]) => items.length === 0 ? (
    <div className="text-center py-12 text-muted-foreground">暂无记录</div>
  ) : (
    <div className="grid gap-3">
      {items.map(d => (
        <Card key={d.id} className="hover:shadow-md transition-shadow">
          <CardContent className="p-4">
            <div className="app-card-row">
              <div className="app-card-main">
                <div className="app-card-title-line">
                  <span className="app-card-title">{d.title}</span>
                  <Badge variant={STATUS_MAP[d.status]?.variant ?? 'outline'}>
                    {STATUS_MAP[d.status]?.label ?? d.status}
                  </Badge>
                  <span className={`text-xs px-2 py-0.5 rounded-full ${
                    d.priority === 'urgent' ? 'bg-red-100 text-red-700' :
                    d.priority === 'high' ? 'bg-orange-100 text-orange-700' : 'bg-blue-100 text-blue-700'
                  }`}>{d.priority}</span>
                </div>
                <div className="app-card-meta">
                  {d.dueDate && <span>截止: {d.dueDate.slice(0, 16).replace('T', ' ')}</span>}
                  <span>{d.createTime?.slice(0, 16).replace('T', ' ')}</span>
                </div>
              </div>
              {d.status !== 'completed' && tab === 'received' && (
                <Button className="w-full sm:w-auto" size="sm" variant="outline" onClick={async () => {
                  await dispatchApi.complete(d.id, '已完成');
                  toast.success('已标记完成');
                  void refresh();
                }}>
                  <CheckCircle2 className="h-4 w-4 mr-1" /> 完成
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Send className="h-6 w-6" /> 工作派发
        </h1>
        <Button onClick={() => setShowCreate(true)}><Plus className="h-4 w-4 mr-1" /> 新建派发</Button>
      </div>

      {showCreate && (
        <Card>
          <CardContent className="p-4 space-y-3">
            <Input placeholder="派发标题" value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))} />
            <textarea className="w-full min-h-[60px] rounded-md border border-input bg-background px-3 py-2 text-sm"
              placeholder="任务描述" value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
            <div className="grid gap-3 md:grid-cols-2">
              <select className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={form.deptId}
                onChange={e => setForm(f => ({ ...f, deptId: e.target.value, assigneeId: '' }))}>
                <option value="">请选择部门</option>
                {departments.map(dept => (
                  <option key={dept.id} value={dept.id}>{dept.name}</option>
                ))}
              </select>
              <select className="h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={form.assigneeId}
                disabled={!form.deptId}
                onChange={e => setForm(f => ({ ...f, assigneeId: e.target.value }))}>
                <option value="">请选择人员</option>
                {users.map(user => (
                  <option key={user.id} value={user.id}>{user.nickname || user.username}</option>
                ))}
              </select>
            </div>
            <div className="app-form-actions">
              <Button onClick={onCreate}>派发</Button>
              <Button variant="outline" onClick={() => setShowCreate(false)}>取消</Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Tabs value={tab} onValueChange={setTab}>
        <TabsList className="grid w-full grid-cols-2 sm:w-auto">
          <TabsTrigger value="received" className="gap-1"><Inbox className="h-4 w-4" /> 我收到的</TabsTrigger>
          <TabsTrigger value="sent" className="gap-1"><Send className="h-4 w-4" /> 我派发的</TabsTrigger>
        </TabsList>
        <TabsContent value="received">{loading ? <div className="text-center py-8">加载中...</div> : renderList(receivedList)}</TabsContent>
        <TabsContent value="sent">{loading ? <div className="text-center py-8">加载中...</div> : renderList(sentList)}</TabsContent>
      </Tabs>
    </div>
  );
}
