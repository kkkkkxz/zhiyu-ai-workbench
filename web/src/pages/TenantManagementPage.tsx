import { useEffect, useState } from 'react';
import { Building2, Plus, RefreshCw, Users } from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { tenantApi } from '@/api/tenant';
import type { TenantInfo, TenantUser } from '@/types/api';

export default function TenantManagementPage() {
  const [tenants, setTenants] = useState<TenantInfo[]>([]);
  const [selected, setSelected] = useState<TenantInfo | null>(null);
  const [users, setUsers] = useState<TenantUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ code: '', name: '' });

  const loadTenants = async () => {
    setLoading(true);
    try {
      const page = await tenantApi.listTenants({ page: 1, size: 50 });
      setTenants(page.records ?? []);
      setSelected((current) => current ?? page.records?.[0] ?? null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadTenants();
  }, []);

  useEffect(() => {
    const loadUsers = async () => {
      if (!selected) {
        setUsers([]);
        return;
      }
      const page = await tenantApi.listUsers(selected.id, { page: 1, size: 10 });
      setUsers(page.records ?? []);
    };
    void loadUsers();
  }, [selected]);

  const createTenant = async () => {
    if (!form.code.trim() || !form.name.trim()) {
      toast.error('请填写租户编码和名称');
      return;
    }
    const tenant = await tenantApi.createTenant({
      code: form.code.trim(),
      name: form.name.trim(),
    });
    toast.success('租户已创建');
    setForm({ code: '', name: '' });
    setTenants((prev) => [tenant, ...prev]);
    setSelected(tenant);
  };

  return (
    <div className="app-page-wide">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Building2 className="h-6 w-6" />
          租户管理
        </h1>
        <div className="app-page-actions">
          <Button variant="outline" onClick={() => void loadTenants()}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">新建租户</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="app-form-grid">
                <Input placeholder="租户编码" value={form.code} onChange={(e) => setForm((v) => ({ ...v, code: e.target.value }))} />
                <Input placeholder="租户名称" value={form.name} onChange={(e) => setForm((v) => ({ ...v, name: e.target.value }))} />
              </div>
              <Button className="w-full sm:w-auto" onClick={createTenant}>
                <Plus className="mr-1 h-4 w-4" />
                新建租户
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">租户列表</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="py-10 text-center text-sm text-muted-foreground">加载中...</div>
              ) : tenants.length === 0 ? (
                <div className="py-10 text-center text-sm text-muted-foreground">暂无租户</div>
              ) : (
                <div className="grid gap-2">
                  {tenants.map((tenant) => (
                    <button
                      key={tenant.id}
                      type="button"
                      className={`rounded-lg border p-3 text-left transition ${
                        selected?.id === tenant.id ? 'border-primary bg-primary/5' : 'hover:bg-accent'
                      }`}
                      onClick={() => setSelected(tenant)}
                    >
                      <div className="app-card-title-line">
                        <span className="app-card-title">{tenant.name}</span>
                        <Badge variant={tenant.status === 1 ? 'default' : 'secondary'}>
                          {tenant.status === 1 ? '启用' : '停用'}
                        </Badge>
                      </div>
                      <div className="app-card-meta">
                        <span>{tenant.code}</span>
                        <span>用户上限 {tenant.maxUsers ?? '-'}</span>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Users className="h-5 w-5" />
              {selected ? `${selected.name} 用户` : '租户用户'}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!selected ? (
              <div className="py-10 text-center text-sm text-muted-foreground">请选择租户</div>
            ) : users.length === 0 ? (
              <div className="py-10 text-center text-sm text-muted-foreground">暂无用户</div>
            ) : (
              <div className="grid gap-3">
                {users.map((user) => (
                  <div key={user.id} className="app-card-row rounded-lg border p-3">
                    <div className="app-card-main">
                      <div className="app-card-title-line">
                        <span className="app-card-title">{user.nickname || user.username}</span>
                        <Badge variant={user.status === 1 ? 'default' : 'secondary'}>
                          {user.status === 1 ? '启用' : '停用'}
                        </Badge>
                      </div>
                      <div className="app-card-meta">
                        <span>@{user.username}</span>
                        {user.email ? <span>{user.email}</span> : null}
                        {user.phone ? <span>{user.phone}</span> : null}
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {user.roles?.map((role) => (
                        <Badge key={role} variant="outline">{role}</Badge>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
