import { useState } from 'react';
import { toast } from 'sonner';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { kbApi } from '@/api/kb';
import type { CreateKbRequest, KnowledgeBase } from '@/types/api';

interface CreateKbDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (kb: KnowledgeBase) => void;
}

const VISIBILITY_OPTIONS: Array<{
  value: NonNullable<CreateKbRequest['visibility']>;
  label: string;
  desc: string;
}> = [
  { value: 'PRIVATE', label: '仅我可见', desc: '只有创建者可见' },
  { value: 'DEPT', label: '部门可见', desc: '同部门成员共享' },
  { value: 'ROLE', label: '角色可见', desc: '指定角色成员共享' },
  { value: 'PUBLIC', label: '租户内公开', desc: '租户内所有人可见' },
];

export function CreateKbDialog({
  open,
  onOpenChange,
  onCreated,
}: CreateKbDialogProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [visibility, setVisibility] =
    useState<NonNullable<CreateKbRequest['visibility']>>('PRIVATE');
  const [loading, setLoading] = useState(false);

  const reset = () => {
    setName('');
    setDescription('');
    setVisibility('PRIVATE');
  };

  const onSubmit = async () => {
    if (!name.trim()) {
      toast.error('请输入知识库名称');
      return;
    }
    setLoading(true);
    try {
      const kb = await kbApi.create({
        name: name.trim(),
        description: description.trim() || undefined,
        visibility,
      });
      toast.success('知识库已创建');
      onCreated(kb);
      onOpenChange(false);
      reset();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) reset();
      }}
    >
      <DialogContent className="max-h-[92vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>新建知识库</DialogTitle>
          <DialogDescription>
            知识库用于组织文档与问答上下文，可在对话页面挂载使用。
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="kb-name">名称</Label>
            <Input
              id="kb-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="例如：产品手册 / 售前 FAQ"
              maxLength={128}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="kb-desc">描述</Label>
            <Textarea
              id="kb-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="简要说明这个知识库的用途"
              maxLength={1000}
              rows={3}
            />
          </div>
          <div className="space-y-2">
            <Label>可见范围</Label>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {VISIBILITY_OPTIONS.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setVisibility(opt.value)}
                  className={`rounded-lg border p-3 text-left text-sm transition-colors ${
                    visibility === opt.value
                      ? 'border-primary bg-primary/5'
                      : 'border-border hover:bg-accent'
                  }`}
                >
                  <div className="font-medium">{opt.label}</div>
                  <div className="mt-0.5 text-xs text-muted-foreground">{opt.desc}</div>
                </button>
              ))}
            </div>
          </div>
        </div>
        <DialogFooter className="flex-col gap-2 sm:flex-row">
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={onSubmit} disabled={loading}>
            {loading ? '创建中...' : '创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
