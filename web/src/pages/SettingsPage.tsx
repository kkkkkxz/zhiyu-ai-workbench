import { ShieldCheck, UserRound } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ThemeToggle } from '@/components/common/ThemeToggle';
import { useAuthStore } from '@/stores/auth';

export default function SettingsPage() {
  const username = useAuthStore((s) => s.username);
  const nickname = useAuthStore((s) => s.nickname);
  const tenantCode = useAuthStore((s) => s.tenantCode);
  const tenantId = useAuthStore((s) => s.tenantId);
  const roles = useAuthStore((s) => s.roles);
  const permissions = useAuthStore((s) => s.permissions);

  return (
    <div className="app-page-narrow">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <UserRound className="h-6 w-6" />
          个人设置
        </h1>
        <ThemeToggle />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>账号信息</CardTitle>
          <CardDescription>当前登录账号和所属租户信息。</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
          <InfoItem label="用户名" value={username || '-'} />
          <InfoItem label="昵称" value={nickname || '-'} />
          <InfoItem label="租户编码" value={tenantCode || '-'} />
          <InfoItem label="租户 ID" value={tenantId || '-'} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5" />
            权限信息
          </CardTitle>
          <CardDescription>角色和权限由管理员统一分配。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <div className="mb-2 text-sm font-medium">角色</div>
            <div className="flex flex-wrap gap-2">
              {roles.length ? roles.map((role) => (
                <Badge key={role} variant="outline">{role}</Badge>
              )) : <span className="text-sm text-muted-foreground">暂无角色</span>}
            </div>
          </div>
          <div>
            <div className="mb-2 text-sm font-medium">权限</div>
            <div className="flex max-h-48 flex-wrap gap-2 overflow-y-auto rounded-lg border bg-muted/20 p-3">
              {permissions.length ? permissions.map((permission) => (
                <Badge key={permission} variant="secondary">{permission}</Badge>
              )) : <span className="text-sm text-muted-foreground">暂无权限</span>}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function InfoItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border bg-muted/20 p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 min-w-0 break-all font-medium">{value}</div>
    </div>
  );
}
