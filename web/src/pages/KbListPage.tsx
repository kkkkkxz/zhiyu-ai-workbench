import { useEffect, useState } from 'react';
import { Database, Plus, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { CreateKbDialog } from '@/components/kb/CreateKbDialog';
import { kbApi } from '@/api/kb';
import type { KnowledgeBase } from '@/types/api';

const VISIBILITY_LABEL: Record<string, string> = {
  PUBLIC: '租户公开',
  DEPT: '部门可见',
  ROLE: '角色可见',
  USER: '指定用户',
  PRIVATE: '仅我可见',
};

export default function KbListPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<KnowledgeBase | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      setItems(await kbApi.list());
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="app-page-wide">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Database className="h-6 w-6" />
          知识库
        </h1>
        <div className="app-page-actions">
          <Button onClick={() => setCreating(true)}>
            <Plus className="mr-1 h-4 w-4" />
            新建知识库
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-sm text-muted-foreground">加载中...</div>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-muted-foreground">
            暂无知识库，先创建一个用于上传文档和挂载问答。
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((kb) => (
            <Card key={kb.id} className="transition-shadow hover:shadow-md">
              <CardHeader className="pb-2">
                <div className="app-card-row">
                  <CardTitle className="min-w-0 break-words text-base">{kb.name}</CardTitle>
                  <Badge variant="outline">{VISIBILITY_LABEL[kb.visibility] ?? kb.visibility}</Badge>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="min-h-10 text-sm text-muted-foreground">
                  {kb.description || '暂无描述'}
                </p>
                <div className="flex flex-col gap-2 sm:flex-row">
                  <Button className="flex-1" variant="outline" onClick={() => navigate(`/kb/${kb.id}`)}>
                    查看文档
                  </Button>
                  <Button
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    onClick={() => setDeleting(kb)}
                  >
                    <Trash2 className="mr-1 h-4 w-4" />
                    删除
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <CreateKbDialog
        open={creating}
        onOpenChange={setCreating}
        onCreated={(kb) => setItems((prev) => [kb, ...prev])}
      />
      <ConfirmDialog
        open={!!deleting}
        title="删除知识库"
        description={`确定要删除“${deleting?.name ?? ''}”吗？`}
        destructive
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
        onConfirm={async () => {
          if (!deleting) return;
          await kbApi.remove(deleting.id);
          toast.success('已删除');
          setItems((prev) => prev.filter((item) => item.id !== deleting.id));
          setDeleting(null);
        }}
      />
    </div>
  );
}
