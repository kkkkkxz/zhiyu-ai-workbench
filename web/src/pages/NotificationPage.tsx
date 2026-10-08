import { useEffect, useState } from 'react';
import { Bell, CheckCheck, Mail, MailOpen } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { notificationApi, type Notification } from '@/api/notification';

export default function NotificationPage() {
  const [list, setList] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);

  const refresh = async () => {
    setLoading(true);
    try {
      const [page, unread] = await Promise.all([
        notificationApi.list(),
        notificationApi.unreadCount(),
      ]);
      setList(page.records ?? []);
      setUnreadCount(unread.count ?? 0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Bell className="h-6 w-6" />
          通知中心
          {unreadCount > 0 ? (
            <span className="rounded-full bg-red-500 px-2 py-0.5 text-xs text-white">
              {unreadCount}
            </span>
          ) : null}
        </h1>
        {unreadCount > 0 ? (
          <Button
            variant="outline"
            onClick={async () => {
              await notificationApi.markAllRead();
              toast.success('已全部标记为已读');
              await refresh();
            }}
          >
            <CheckCheck className="mr-1 h-4 w-4" />
            全部已读
          </Button>
        ) : null}
      </div>

      {loading ? (
        <div className="py-12 text-center text-sm text-muted-foreground">加载中...</div>
      ) : list.length === 0 ? (
        <div className="py-12 text-center text-sm text-muted-foreground">暂无通知</div>
      ) : (
        <div className="grid gap-2">
          {list.map((item) => (
            <Card
              key={item.id}
              className={`transition-shadow ${item.isRead === 0 ? 'border-l-4 border-l-blue-500' : ''}`}
            >
              <CardContent className="p-4">
                <div className="app-card-row">
                  <div className="flex min-w-0 items-start gap-3">
                    {item.isRead === 0 ? (
                      <Mail className="mt-0.5 h-4 w-4 shrink-0 text-blue-500" />
                    ) : (
                      <MailOpen className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                    )}
                    <div className="min-w-0">
                      <div className={`font-medium ${item.isRead === 0 ? '' : 'text-muted-foreground'}`}>
                        {item.title}
                      </div>
                      {item.content ? (
                        <div className="mt-1 text-sm text-muted-foreground">{item.content}</div>
                      ) : null}
                      <div className="mt-1 text-xs text-muted-foreground">
                        {item.createTime?.slice(0, 16).replace('T', ' ')}
                      </div>
                    </div>
                  </div>
                  {item.isRead === 0 ? (
                    <Button
                      className="w-full sm:w-auto"
                      size="sm"
                      variant="ghost"
                      onClick={async () => {
                        await notificationApi.markRead(item.id);
                        await refresh();
                      }}
                    >
                      标记已读
                    </Button>
                  ) : null}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
