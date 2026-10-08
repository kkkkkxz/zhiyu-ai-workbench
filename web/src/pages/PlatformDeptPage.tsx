import { useEffect, useState } from 'react';
import { FolderTree, Plus, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { platformApi, type DepartmentNode } from '@/api/platform';
import { useAuthStore } from '@/stores/auth';

export default function PlatformDeptPage() {
  const tenantId = useAuthStore((s) => s.tenantId);
  const [items, setItems] = useState<DepartmentNode[]>([]);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);

  const load = async () => {
    if (!tenantId) return;
    setLoading(true);
    try {
      setItems(await platformApi.deptTree(tenantId));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [tenantId]);

  const create = async () => {
    if (!tenantId || !name.trim()) return;
    await platformApi.createDept(tenantId, { name: name.trim() });
    toast.success('部门已创建');
    setName('');
    await load();
  };

  return (
    <div className="app-page-narrow">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <FolderTree className="h-6 w-6" />
          部门管理
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">新建部门</CardTitle>
        </CardHeader>
        <CardContent className="app-toolbar">
          <Input placeholder="部门名称" value={name} onChange={(e) => setName(e.target.value)} />
          <Button onClick={create}>
            <Plus className="mr-1 h-4 w-4" />
            新建
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">部门列表</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-10 text-center text-sm text-muted-foreground">加载中...</div>
          ) : items.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">暂无部门</div>
          ) : (
            <div className="grid gap-3">
              {items.map((item) => (
                <DeptCard key={item.id} node={item} tenantId={tenantId || ''} onChanged={load} />
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function DeptCard({ node, tenantId, onChanged }: { node: DepartmentNode; tenantId: string; onChanged: () => Promise<void> }) {
  return (
    <div className="rounded-lg border p-3">
      <div className="app-card-row">
        <div className="app-card-main">
          <div className="app-card-title-line">
            <span className="app-card-title">{node.name}</span>
            <Badge variant={node.status === 1 ? 'default' : 'secondary'}>
              {node.status === 1 ? '启用' : '停用'}
            </Badge>
          </div>
          <div className="app-card-meta">
            <span>编码：{node.code || '-'}</span>
            <span>层级：{node.level}</span>
          </div>
        </div>
        <Button
          className="w-full sm:w-auto"
          variant="ghost"
          onClick={async () => {
            await platformApi.deleteDept(tenantId, node.id);
            toast.success('部门已删除');
            await onChanged();
          }}
        >
          <Trash2 className="mr-1 h-4 w-4" />
          删除
        </Button>
      </div>
      {node.children?.length ? (
        <div className="mt-3 grid gap-2 border-l pl-3">
          {node.children.map((child) => (
            <DeptCard key={child.id} node={child} tenantId={tenantId} onChanged={onChanged} />
          ))}
        </div>
      ) : null}
    </div>
  );
}
