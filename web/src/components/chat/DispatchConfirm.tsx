import { useState } from 'react';
import { Send, Check, X, Clock, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { aiDispatchApi, type DispatchPreview } from '@/api/aiDispatch';

interface DispatchConfirmProps {
  preview: DispatchPreview;
  onConfirmed?: () => void;
  onCancelled?: () => void;
}

const PRIORITY_LABEL: Record<string, { label: string; className: string }> = {
  urgent: { label: '紧急', className: 'bg-red-100 text-red-700' },
  high: { label: '高', className: 'bg-orange-100 text-orange-700' },
  medium: { label: '中', className: 'bg-blue-100 text-blue-700' },
  low: { label: '低', className: 'bg-slate-100 text-slate-700' },
};

export function DispatchConfirm({ preview, onConfirmed, onCancelled }: DispatchConfirmProps) {
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleConfirm = async () => {
    setLoading(true);
    try {
      await aiDispatchApi.confirm(preview);
      toast.success(`任务已派发给 ${preview.targetUserName}`);
      setDone(true);
      onConfirmed?.();
    } catch (e: any) {
      toast.error(e?.message || '派发失败');
    } finally {
      setLoading(false);
    }
  };

  if (done) {
    return (
      <Card className="border-green-200 bg-green-50/50 my-2">
        <CardContent className="p-4 flex items-center gap-2 text-green-700">
          <Check className="h-5 w-5" />
          <span className="font-medium">已成功派发给 {preview.targetUserName}，对方已收到通知和日程提醒</span>
        </CardContent>
      </Card>
    );
  }

  const pri = PRIORITY_LABEL[preview.priority] ?? PRIORITY_LABEL.medium;

  return (
    <Card className="border-amber-200 bg-amber-50/30 my-2">
      <CardContent className="p-4 space-y-3">
        <div className="flex items-center gap-2 text-sm font-medium text-amber-700">
          <AlertTriangle className="h-4 w-4" />
          <span>派发确认</span>
        </div>

        <div className="space-y-2">
          <div className="flex items-center gap-2">
            <Send className="h-4 w-4 text-muted-foreground" />
            <span className="font-semibold">{preview.taskTitle}</span>
            <span className={`text-xs px-2 py-0.5 rounded-full ${pri.className}`}>{pri.label}</span>
          </div>

          <div className="grid grid-cols-2 gap-2 text-sm">
            <div>
              <span className="text-muted-foreground">接收人：</span>
              <span className="font-medium">{preview.targetUserName}</span>
              {preview.targetDeptName && (
                <Badge variant="outline" className="ml-1 text-xs">{preview.targetDeptName}</Badge>
              )}
            </div>
            {preview.deadline && (
              <div className="flex items-center gap-1">
                <Clock className="h-3 w-3 text-muted-foreground" />
                <span className="text-muted-foreground">截止：</span>
                <span>{preview.deadline.replace('T', ' ').slice(0, 16)}</span>
              </div>
            )}
          </div>

          {preview.taskDescription && (
            <div className="text-sm text-muted-foreground bg-background rounded p-2">
              {preview.taskDescription}
            </div>
          )}
        </div>

        <div className="flex gap-2 pt-1">
          <Button size="sm" onClick={handleConfirm} disabled={loading}>
            <Check className="h-4 w-4 mr-1" />
            {loading ? '派发中...' : '确认派发'}
          </Button>
          <Button size="sm" variant="outline" onClick={onCancelled} disabled={loading}>
            <X className="h-4 w-4 mr-1" /> 取消
          </Button>
        </div>

        <div className="text-xs text-muted-foreground">
          确认后将：① 创建派发任务 ② 出现在对方日程中 ③ 发送通知提醒
        </div>
      </CardContent>
    </Card>
  );
}
