import { useEffect, useMemo, useState } from 'react';
import {
  Bot,
  Building2,
  Check,
  ChevronRight,
  FileKey2,
  RefreshCw,
  Save,
  Settings2,
  ShieldCheck,
  ToggleLeft,
  ToggleRight,
  Users,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';

import { tenantApi } from '@/api/tenant';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { useAuthStore } from '@/stores/auth';
import { useTenantFeatureStore } from '@/stores/tenantFeatures';
import type { TenantAiConfig, TenantFeature, TenantUser } from '@/types/api';

const MAX_CHAT_TEMPERATURE = 0.15;

function clampTemperature(value: number) {
  return Math.min(MAX_CHAT_TEMPERATURE, Math.max(0, value));
}

export default function TenantAdminPage() {
  const navigate = useNavigate();
  const tenantId = useAuthStore((s) => s.tenantId);
  const tenantCode = useAuthStore((s) => s.tenantCode);
  const username = useAuthStore((s) => s.username);
  const roles = useAuthStore((s) => s.roles);
  const refreshFeatures = useTenantFeatureStore((s) => s.refresh);

  const [users, setUsers] = useState<TenantUser[]>([]);
  const [features, setFeatures] = useState<TenantFeature[]>([]);
  const [aiConfig, setAiConfig] = useState<TenantAiConfig | null>(null);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [loadingFeatures, setLoadingFeatures] = useState(true);
  const [loadingAi, setLoadingAi] = useState(true);
  const [savingFeatureCode, setSavingFeatureCode] = useState<string | null>(null);
  const [savingAi, setSavingAi] = useState(false);
  const [aiForm, setAiForm] = useState({
    provider: '',
    baseUrl: '',
    apiKey: '',
    chatModel: '',
    embeddingModel: '',
    visionModel: '',
    temperature: '0.1',
    enabled: true,
    remark: '',
  });

  const loadUsers = async () => {
    if (!tenantId) return;
    setLoadingUsers(true);
    try {
      const page = await tenantApi.listUsers(tenantId, { page: 1, size: 20 });
      setUsers(page.records ?? []);
    } finally {
      setLoadingUsers(false);
    }
  };

  const loadFeatures = async () => {
    if (!tenantId) return;
    setLoadingFeatures(true);
    try {
      setFeatures(await tenantApi.listFeatures(tenantId));
      await refreshFeatures(tenantId);
    } finally {
      setLoadingFeatures(false);
    }
  };

  const loadAiConfig = async () => {
    if (!tenantId) return;
    setLoadingAi(true);
    try {
      const config = await tenantApi.getAiConfig(tenantId);
      setAiConfig(config);
      setAiForm({
        provider: config.provider ?? '',
        baseUrl: config.baseUrl ?? '',
        apiKey: '',
        chatModel: config.chatModel ?? '',
        embeddingModel: config.embeddingModel ?? '',
        visionModel: config.visionModel ?? '',
        temperature: String(clampTemperature(config.temperature ?? 0.1)),
        enabled: Boolean(config.enabled),
        remark: config.remark ?? '',
      });
    } finally {
      setLoadingAi(false);
    }
  };

  useEffect(() => {
    void loadUsers();
    void loadFeatures();
    void loadAiConfig();
  }, [tenantId]);

  const groupedFeatures = useMemo(() => {
    const groups = new Map<string, TenantFeature[]>();
    for (const item of features) {
      const list = groups.get(item.category) ?? [];
      list.push(item);
      groups.set(item.category, list);
    }
    return Array.from(groups.entries()).map(([category, items]) => ({
      category,
      items: items.sort((a, b) => a.sort - b.sort),
    }));
  }, [features]);

  const saveFeature = async (feature: TenantFeature, enabled: boolean) => {
    if (!tenantId) return;
    setSavingFeatureCode(feature.code);
    try {
      const updated = await tenantApi.updateFeature(tenantId, feature.code, enabled);
      setFeatures((prev) => prev.map((item) => (item.code === updated.code ? updated : item)));
      await refreshFeatures(tenantId);
      toast.success(`已${enabled ? '启用' : '停用'} ${updated.name}`);
    } finally {
      setSavingFeatureCode(null);
    }
  };

  const saveAiConfig = async () => {
    if (!tenantId) return;
    setSavingAi(true);
    try {
      const temperature = Number.parseFloat(aiForm.temperature);
      const updated = await tenantApi.updateAiConfig(tenantId, {
        provider: aiForm.provider.trim() || undefined,
        baseUrl: aiForm.baseUrl.trim() || undefined,
        apiKey: aiForm.apiKey.trim() || undefined,
        chatModel: aiForm.chatModel.trim() || undefined,
        embeddingModel: aiForm.embeddingModel.trim() || undefined,
        visionModel: aiForm.visionModel.trim() || undefined,
        temperature: Number.isFinite(temperature) ? clampTemperature(temperature) : undefined,
        enabled: aiForm.enabled,
        remark: aiForm.remark.trim() || undefined,
      });
      setAiConfig(updated);
      setAiForm((prev) => ({ ...prev, apiKey: '', remark: updated.remark ?? prev.remark }));
      toast.success('AI 配置已更新，后续请求将立即生效');
    } finally {
      setSavingAi(false);
    }
  };

  return (
    <div className="app-page-wide">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Building2 className="h-6 w-6" />
          公司控制台
        </h1>
        <div className="app-page-actions">
          <Button variant="outline" onClick={() => void loadUsers()}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
          <Button onClick={() => navigate('/templates')}>
            <ChevronRight className="mr-1 h-4 w-4" />
            模板中心
          </Button>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">租户概览</CardTitle>
            <CardDescription>当前公司账号、角色和可用能力。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            <InfoLine label="租户编码" value={tenantCode || tenantId || '-'} />
            <InfoLine label="当前账号" value={username || '-'} />
            <InfoLine label="当前角色" value={roles.join(' / ') || '-'} />
            <InfoLine label="模板状态" value={features.some((item) => item.enabled) ? '已开通' : '待配置'} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">AI 嵌入业务</CardTitle>
            <CardDescription>合同、工单、附件、对话和语音都读取这套租户配置。</CardDescription>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-muted-foreground">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-4 w-4" />
              支持实时替换 Key，保存后后续请求立即切换。
            </div>
            <div className="flex items-center gap-2">
              <FileKey2 className="h-4 w-4" />
              仅显示脱敏值，不回显明文。
            </div>
            <div className="flex items-center gap-2">
              <Bot className="h-4 w-4" />
              可按租户切换模型供应商、对话模型、向量模型和视觉模型。
            </div>
          </CardContent>
        </Card>
      </div>

      <Tabs defaultValue="features" className="space-y-4">
        <TabsList className="grid w-full grid-cols-3 sm:w-auto">
          <TabsTrigger value="features" className="gap-1">
            <Settings2 className="h-4 w-4" />
            功能开关
          </TabsTrigger>
          <TabsTrigger value="ai" className="gap-1">
            <Bot className="h-4 w-4" />
            AI 配置
          </TabsTrigger>
          <TabsTrigger value="members" className="gap-1">
            <Users className="h-4 w-4" />
            成员管理
          </TabsTrigger>
        </TabsList>

        <TabsContent value="features" className="space-y-4">
          {loadingFeatures ? (
            <Card>
              <CardContent className="py-10 text-center text-sm text-muted-foreground">加载功能中...</CardContent>
            </Card>
          ) : (
            groupedFeatures.map((group) => (
              <Card key={group.category}>
                <CardHeader>
                  <CardTitle className="text-base">{group.category}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  {group.items.map((feature) => (
                    <div
                      key={feature.code}
                      className="flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between"
                    >
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-medium">{feature.name}</span>
                          <Badge variant={feature.enabled ? 'default' : 'secondary'}>
                            {feature.enabled ? '已启用' : '已停用'}
                          </Badge>
                          {feature.sourceTemplateCode ? (
                            <Badge variant="outline">来源：{feature.sourceTemplateCode}</Badge>
                          ) : null}
                        </div>
                        <div className="text-sm text-muted-foreground">{feature.description}</div>
                        <div className="text-xs text-muted-foreground">编码：{feature.code}</div>
                      </div>
                      <Button
                        variant={feature.enabled ? 'outline' : 'default'}
                        disabled={savingFeatureCode === feature.code}
                        onClick={() => void saveFeature(feature, !feature.enabled)}
                      >
                        {savingFeatureCode === feature.code ? (
                          '保存中...'
                        ) : feature.enabled ? (
                          <>
                            <ToggleLeft className="mr-1 h-4 w-4" />
                            关闭
                          </>
                        ) : (
                          <>
                            <ToggleRight className="mr-1 h-4 w-4" />
                            开启
                          </>
                        )}
                      </Button>
                    </div>
                  ))}
                </CardContent>
              </Card>
            ))
          )}
        </TabsContent>

        <TabsContent value="ai">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">租户 AI 配置</CardTitle>
              <CardDescription>Key 一次保存，聊天、向量、附件视觉解析、语音相关能力都会读取租户级配置。</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-4">
              <div className="grid gap-3 md:grid-cols-2">
                <Field label="供应商" value={aiForm.provider} onChange={(value) => setAiForm((prev) => ({ ...prev, provider: value }))} />
                <Field label="Base URL" value={aiForm.baseUrl} onChange={(value) => setAiForm((prev) => ({ ...prev, baseUrl: value }))} />
                <Field label="Chat 模型" value={aiForm.chatModel} onChange={(value) => setAiForm((prev) => ({ ...prev, chatModel: value }))} />
                <Field label="Embedding 模型" value={aiForm.embeddingModel} onChange={(value) => setAiForm((prev) => ({ ...prev, embeddingModel: value }))} />
                <Field label="Vision 模型" value={aiForm.visionModel} onChange={(value) => setAiForm((prev) => ({ ...prev, visionModel: value }))} />
                <Field label="Temperature（0-0.15）" value={aiForm.temperature} onChange={(value) => setAiForm((prev) => ({ ...prev, temperature: value }))} />
              </div>

              <div className="space-y-2">
                <Label htmlFor="apiKey">API Key</Label>
                <Input
                  id="apiKey"
                  type="password"
                  value={aiForm.apiKey}
                  placeholder={aiConfig?.apiKeyMask || '输入新 Key 进行替换'}
                  onChange={(e) => setAiForm((prev) => ({ ...prev, apiKey: e.target.value }))}
                />
                <div className="text-xs text-muted-foreground">
                  当前状态：{aiConfig?.apiKeyConfigured ? `已配置（${aiConfig.apiKeyMask || '已脱敏'}）` : '未配置'}
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="remark">备注</Label>
                <Textarea
                  id="remark"
                  value={aiForm.remark}
                  onChange={(e) => setAiForm((prev) => ({ ...prev, remark: e.target.value }))}
                  placeholder="例如：2026Q2 续费后的主模型配置"
                />
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <Button
                  type="button"
                  variant={aiForm.enabled ? 'default' : 'outline'}
                  onClick={() => setAiForm((prev) => ({ ...prev, enabled: !prev.enabled }))}
                >
                  {aiForm.enabled ? <Check className="mr-1 h-4 w-4" /> : null}
                  {aiForm.enabled ? '已启用' : '已停用'}
                </Button>
                <Button disabled={savingAi || loadingAi} onClick={() => void saveAiConfig()}>
                  <Save className="mr-1 h-4 w-4" />
                  {savingAi ? '保存中...' : '保存配置'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="members">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">租户成员</CardTitle>
              <CardDescription>管理员可继续在这里查看成员结构，后续可扩展邀请、重置密码和角色分配。</CardDescription>
            </CardHeader>
            <CardContent>
              {loadingUsers ? (
                <div className="py-10 text-center text-sm text-muted-foreground">加载中...</div>
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
                          <Badge key={role} variant="outline">
                            {role}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="mt-1 text-sm font-medium">{value}</div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}
