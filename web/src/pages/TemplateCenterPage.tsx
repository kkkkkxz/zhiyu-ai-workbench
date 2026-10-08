import { useEffect, useMemo, useState } from 'react';
import { Boxes, CheckCircle2, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

import { templateApi, type TemplateInstallRecord, type TemplatePackage } from '@/api/template';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useAuthStore } from '@/stores/auth';
import { useTenantFeatureStore } from '@/stores/tenantFeatures';

export default function TemplateCenterPage() {
  const [packages, setPackages] = useState<TemplatePackage[]>([]);
  const [installed, setInstalled] = useState<TemplateInstallRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [installing, setInstalling] = useState<string | null>(null);
  const tenantId = useAuthStore((s) => s.tenantId);
  const refreshFeatures = useTenantFeatureStore((s) => s.refresh);

  const installedCodes = useMemo(() => new Set(installed.map((item) => item.packageCode)), [installed]);

  const refresh = async () => {
    setLoading(true);
    try {
      const [pkgList, installedList] = await Promise.all([
        templateApi.list(),
        templateApi.installed(),
      ]);
      setPackages(pkgList ?? []);
      setInstalled(installedList ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refresh();
  }, []);

  const install = async (code: string) => {
    setInstalling(code);
    try {
      await templateApi.install(code);
      toast.success('模板安装成功！已初始化审批流程、示范客户等数据，可前往各模块查看。');
      if (tenantId) {
        await refreshFeatures(tenantId);
      }
      await refresh();
    } finally {
      setInstalling(null);
    }
  };

  return (
    <div className="app-page">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Boxes className="h-6 w-6" />
          模板中心
        </h1>
      </div>

      <Card className="border-primary/20 bg-primary/5">
        <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
          <Sparkles className="h-7 w-7 shrink-0 text-primary" />
          <div className="min-w-0 flex-1">
            <div className="font-medium">用模板开通企业业务能力</div>
            <div className="text-sm text-muted-foreground">
              模板会沉淀菜单、流程、角色、指标、文档和 AI Agent。后续每个行业的小业务都会在这里扩展。
            </div>
          </div>
        </CardContent>
      </Card>

      {loading ? (
        <div className="py-12 text-center text-muted-foreground">正在加载模板...</div>
      ) : (
        <div className="grid gap-4 lg:grid-cols-3">
          {packages.map((item) => {
            const isInstalled = installedCodes.has(item.code);
            const modules = parseJsonArray(item.modulesJson);
            const agents = parseJsonArray(item.aiAgentsJson);
            return (
              <Card key={item.code} className="flex flex-col">
                <CardContent className="flex flex-1 flex-col gap-4 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-base font-semibold">{item.name}</div>
                      <div className="mt-1 text-sm text-muted-foreground">{item.description}</div>
                    </div>
                    <Badge variant={isInstalled ? 'default' : 'outline'}>
                      {isInstalled ? '已安装' : categoryLabel(item.category)}
                    </Badge>
                  </div>

                  <div>
                    <div className="mb-2 text-xs font-medium text-muted-foreground">业务模块</div>
                    <div className="flex flex-wrap gap-1.5">
                      {modules.map((module) => (
                        <Badge key={module} variant="secondary">{module}</Badge>
                      ))}
                    </div>
                  </div>

                  <div>
                    <div className="mb-2 text-xs font-medium text-muted-foreground">AI Agent</div>
                    <div className="flex flex-wrap gap-1.5">
                      {agents.map((agent) => (
                        <Badge key={agent} variant="outline">{agent}</Badge>
                      ))}
                    </div>
                  </div>

                  {item.scenario ? (
                    <div className="rounded-md bg-muted p-3 text-sm text-muted-foreground">
                      {item.scenario}
                    </div>
                  ) : null}

                  <div className="mt-auto">
                    {isInstalled ? (
                      <Button
                        className="w-full gap-1"
                        variant="outline"
                        disabled={installing === item.code}
                        onClick={() => void install(item.code)}
                      >
                        <CheckCircle2 className="h-4 w-4" />
                        {installing === item.code ? '应用中...' : '已安装 · 重新应用'}
                      </Button>
                    ) : (
                      <Button
                        className="w-full"
                        disabled={installing === item.code}
                        onClick={() => void install(item.code)}
                      >
                        {installing === item.code ? '安装中...' : '安装模板'}
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

function parseJsonArray(value?: string) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function categoryLabel(value: string) {
  const map: Record<string, string> = {
    business: '经营',
    project: '项目',
    service: '售后',
    office: '办公',
  };
  return map[value] ?? value;
}
