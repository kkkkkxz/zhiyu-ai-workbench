import { useEffect, useState } from 'react';
import { Bell, BellOff, CalendarDays, Clock, Plus, Sparkles, X } from 'lucide-react';
import { toast } from 'sonner';

import { aiEnhancementApi } from '@/api/aiEnhancement';
import { scheduleApi, type Schedule, type TaskReminder } from '@/api/schedule';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

const TYPE_LABEL: Record<string, string> = {
  event: '事件',
  meeting: '会议',
  reminder: '提醒',
  follow_up: '跟进',
};

function DateTimeField({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (next: string) => void;
  label: string;
}) {
  return (
    <div className="grid gap-2">
      <div className="text-xs text-muted-foreground">{label}</div>
      <Input type="datetime-local" value={value.slice(0, 16)} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function toDateTimeLocal(value: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(
    value.getHours(),
  )}:${pad(value.getMinutes())}`;
}

function nowDateTimeLocal() {
  return toDateTimeLocal(new Date());
}

function oneHourLaterDateTimeLocal() {
  const next = new Date();
  next.setHours(next.getHours() + 1);
  return toDateTimeLocal(next);
}

export default function SchedulePage() {
  const [tab, setTab] = useState('schedule');
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [reminders, setReminders] = useState<TaskReminder[]>([]);
  const [loading, setLoading] = useState(false);
  const [composer, setComposer] = useState<'schedule' | 'reminder' | null>(null);
  const [form, setForm] = useState({
    title: '',
    startTime: '',
    endTime: '',
    type: 'event',
    description: '',
  });
  const [reminderForm, setReminderForm] = useState({
    title: '',
    remindTime: '',
    content: '',
  });
  const [aiLoading, setAiLoading] = useState<string | null>(null);

  const refresh = async () => {
    setLoading(true);
    try {
      const [schedulePage, reminderPage] = await Promise.all([
        scheduleApi.list(),
        scheduleApi.myReminders(),
      ]);
      setSchedules(schedulePage.records ?? []);
      setReminders(reminderPage.records ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const createSchedule = async () => {
    if (!form.title || !form.startTime) {
      toast.error('请填写标题和开始时间');
      return;
    }
    await scheduleApi.create({
      title: form.title,
      startTime: form.startTime,
      endTime: form.endTime || undefined,
      type: form.type,
      description: form.description || undefined,
    });
    toast.success('日程已创建');
    setComposer(null);
    setForm({ title: '', startTime: '', endTime: '', type: 'event', description: '' });
    await refresh();
  };

  const createReminder = async () => {
    if (!reminderForm.title || !reminderForm.remindTime) {
      toast.error('请填写提醒标题和时间');
      return;
    }
    await scheduleApi.createReminder(reminderForm);
    toast.success('提醒已创建');
    setComposer(null);
    setReminderForm({ title: '', remindTime: '', content: '' });
    await refresh();
  };

  const generateSmartSchedules = async () => {
    setAiLoading('schedules');
    try {
      const result = await aiEnhancementApi.generateSmartSchedules();
      toast.success(`AI 已生成 ${result.length} 个智能日程`);
      await refresh();
    } finally {
      setAiLoading(null);
    }
  };

  const generateSmartReminders = async () => {
    setAiLoading('reminders');
    try {
      const result = await aiEnhancementApi.generateSmartReminders();
      toast.success(`AI 已生成 ${result.length} 个智能提醒`);
      await refresh();
    } finally {
      setAiLoading(null);
    }
  };

  const showTodayBriefing = async () => {
    setAiLoading('briefing');
    try {
      const result = await aiEnhancementApi.todayBriefing();
      toast.success(String(result.summary ?? '今日播报已生成'));
    } finally {
      setAiLoading(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <CalendarDays className="h-6 w-6" />
          日程管理
        </h1>
        <div className="app-page-actions">
          <Button variant="outline" disabled={aiLoading === 'briefing'} onClick={() => void showTodayBriefing()}>
            <Sparkles className="mr-1 h-4 w-4" />
            今日播报
          </Button>
          <Button variant="outline" disabled={aiLoading === 'schedules'} onClick={() => void generateSmartSchedules()}>
            <Sparkles className="mr-1 h-4 w-4" />
            AI 生成日程
          </Button>
          <Button variant="outline" disabled={aiLoading === 'reminders'} onClick={() => void generateSmartReminders()}>
            <Sparkles className="mr-1 h-4 w-4" />
            AI 生成提醒
          </Button>
          <Button
            variant="outline"
            onClick={() => {
              setReminderForm((prev) => ({
                ...prev,
                remindTime: prev.remindTime || nowDateTimeLocal(),
              }));
              setComposer('reminder');
            }}
          >
            <Bell className="mr-1 h-4 w-4" />
            新建提醒
          </Button>
          <Button
            onClick={() => {
              setForm((prev) => ({
                ...prev,
                startTime: prev.startTime || nowDateTimeLocal(),
                endTime: prev.endTime || oneHourLaterDateTimeLocal(),
              }));
              setComposer('schedule');
            }}
          >
            <Plus className="mr-1 h-4 w-4" />
            新建日程
          </Button>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.4fr_0.6fr]">
        <Card>
          <CardContent className="p-0">
            <Tabs value={tab} onValueChange={setTab} className="space-y-0">
              <TabsList className="grid w-full grid-cols-2 rounded-none rounded-t-xl border-b bg-transparent p-0">
                <TabsTrigger
                  value="schedule"
                  className="h-11 rounded-none border-b-2 border-transparent bg-transparent data-[state=active]:border-foreground data-[state=active]:shadow-none"
                >
                  <CalendarDays className="mr-1 h-4 w-4" />
                  日程
                </TabsTrigger>
                <TabsTrigger
                  value="reminders"
                  className="h-11 rounded-none border-b-2 border-transparent bg-transparent data-[state=active]:border-foreground data-[state=active]:shadow-none"
                >
                  <Bell className="mr-1 h-4 w-4" />
                  提醒 {reminders.length > 0 ? `(${reminders.length})` : ''}
                </TabsTrigger>
              </TabsList>

              <TabsContent value="schedule" className="mt-0 p-4">
                {loading ? (
                  <div className="py-8 text-center text-sm text-muted-foreground">加载中...</div>
                ) : schedules.length === 0 ? (
                  <div className="py-12 text-center text-sm text-muted-foreground">暂无日程</div>
                ) : (
                  <div className="grid gap-3">
                    {schedules.map((item) => (
                      <Card key={item.id} className="transition-shadow hover:shadow-md">
                        <CardContent className="p-4">
                          <div className="app-card-row">
                            <div className="app-card-main">
                              <div className="app-card-title-line">
                                {item.color ? <div className="h-3 w-3 rounded-full" style={{ backgroundColor: item.color }} /> : null}
                                <span className="app-card-title">{item.title}</span>
                                <Badge variant="outline">{TYPE_LABEL[item.type] ?? item.type}</Badge>
                              </div>
                              <div className="app-card-meta">
                                <span className="flex items-center gap-1">
                                  <Clock className="h-3 w-3" />
                                  {item.startTime?.slice(0, 16).replace('T', ' ')}
                                </span>
                                {item.endTime ? <span>~ {item.endTime.slice(11, 16)}</span> : null}
                                {item.location ? <span>{item.location}</span> : null}
                              </div>
                            </div>
                            <Button
                              className="w-full sm:w-auto"
                              size="sm"
                              variant="ghost"
                              onClick={async () => {
                                await scheduleApi.delete(item.id);
                                toast.success('已删除');
                                await refresh();
                              }}
                            >
                              删除
                            </Button>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                )}
              </TabsContent>

              <TabsContent value="reminders" className="mt-0 p-4">
                {reminders.length === 0 ? (
                  <div className="py-12 text-center text-sm text-muted-foreground">暂无提醒</div>
                ) : (
                  <div className="grid gap-3">
                    {reminders.map((item) => (
                      <Card key={item.id}>
                        <CardContent className="app-card-row p-4">
                          <div className="app-card-main">
                            <div className="app-card-title">{item.title}</div>
                            <div className="app-card-meta">
                              <span className="flex items-center gap-1">
                                <Clock className="h-3 w-3" />
                                {item.remindTime?.slice(0, 16).replace('T', ' ')}
                              </span>
                              {item.content ? <span>{item.content}</span> : null}
                            </div>
                          </div>
                          <Button
                            className="w-full sm:w-auto"
                            size="sm"
                            variant="outline"
                            onClick={async () => {
                              await scheduleApi.dismissReminder(item.id);
                              toast.success('已忽略');
                              await refresh();
                            }}
                          >
                            <BellOff className="mr-1 h-4 w-4" />
                            忽略
                          </Button>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                )}
              </TabsContent>
            </Tabs>
          </CardContent>
        </Card>

        <div className="space-y-4">
          {composer === 'schedule' ? (
            <Card>
              <CardContent className="space-y-3 p-4">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold">新建日程</div>
                  <button
                    type="button"
                    className="text-xs text-muted-foreground hover:text-foreground"
                    onClick={() => setComposer(null)}
                  >
                    <X className="mr-1 inline h-3.5 w-3.5" />
                    收起
                  </button>
                </div>
                <Input
                  placeholder="日程标题"
                  value={form.title}
                  onChange={(e) => setForm((prev) => ({ ...prev, title: e.target.value }))}
                />
                <div className="grid gap-2">
                  <DateTimeField
                    label="开始时间"
                    value={form.startTime}
                    onChange={(next) => setForm((prev) => ({ ...prev, startTime: next }))}
                  />
                  <DateTimeField
                    label="结束时间"
                    value={form.endTime}
                    onChange={(next) => setForm((prev) => ({ ...prev, endTime: next }))}
                  />
                </div>
                <select
                  className="h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={form.type}
                  onChange={(e) => setForm((prev) => ({ ...prev, type: e.target.value }))}
                >
                  <option value="event">事件</option>
                  <option value="meeting">会议</option>
                  <option value="reminder">提醒</option>
                  <option value="follow_up">跟进</option>
                </select>
                <textarea
                  className="min-h-[72px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  placeholder="描述"
                  value={form.description}
                  onChange={(e) => setForm((prev) => ({ ...prev, description: e.target.value }))}
                />
                <div className="app-form-actions">
                  <Button onClick={() => void createSchedule()}>保存</Button>
                  <Button variant="outline" onClick={() => setComposer(null)}>
                    取消
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : null}

          {composer === 'reminder' ? (
            <Card>
              <CardContent className="space-y-3 p-4">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold">新建提醒</div>
                  <button
                    type="button"
                    className="text-xs text-muted-foreground hover:text-foreground"
                    onClick={() => setComposer(null)}
                  >
                    <X className="mr-1 inline h-3.5 w-3.5" />
                    收起
                  </button>
                </div>
                <Input
                  placeholder="提醒标题"
                  value={reminderForm.title}
                  onChange={(e) => setReminderForm((prev) => ({ ...prev, title: e.target.value }))}
                />
                <DateTimeField
                  label="提醒时间"
                  value={reminderForm.remindTime}
                  onChange={(next) => setReminderForm((prev) => ({ ...prev, remindTime: next }))}
                />
                <textarea
                  className="min-h-[64px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  placeholder="提醒内容"
                  value={reminderForm.content}
                  onChange={(e) => setReminderForm((prev) => ({ ...prev, content: e.target.value }))}
                />
                <div className="app-form-actions">
                  <Button onClick={() => void createReminder()}>保存</Button>
                  <Button variant="outline" onClick={() => setComposer(null)}>
                    取消
                  </Button>
                </div>
              </CardContent>
            </Card>
          ) : null}
        </div>
      </div>
    </div>
  );
}
