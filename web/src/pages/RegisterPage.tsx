import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Building2, Eye, EyeOff, Sparkles, UserPlus } from 'lucide-react';
import { toast } from 'sonner';

import { authApi } from '@/api/auth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuthStore } from '@/stores/auth';

const USERNAME_RE = /^[a-zA-Z][a-zA-Z0-9_\-.]{2,31}$/;
const PASSWORD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^1[3-9]\d{9}$/;

export default function RegisterPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = params.get('next') || '/chat';
  const setSession = useAuthStore((s) => s.setSession);
  const isAuthed = useAuthStore((s) => s.isAuthenticated());

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPwd, setConfirmPwd] = useState('');
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [tenantId, setTenantId] = useState('');
  const [tenants, setTenants] = useState<{ id: string; code: string; name: string }[]>([]);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);
  const [agreed, setAgreed] = useState(false);

  useEffect(() => {
    if (isAuthed) navigate(next, { replace: true });
  }, [isAuthed, navigate, next]);

  useEffect(() => {
    authApi
      .tenants()
      .then((list) => {
        setTenants(list ?? []);
        if (!tenantId && list?.length) setTenantId(list[0].id);
      })
      .catch(() => setTenants([]));
  }, [tenantId]);

  const pwdStrength = useMemo(() => {
    if (!password) return { score: 0, label: '', color: '' };
    let score = 0;
    if (password.length >= 8) score += 1;
    if (password.length >= 12) score += 1;
    if (/[A-Za-z]/.test(password) && /\d/.test(password)) score += 1;
    if (/[^A-Za-z0-9]/.test(password)) score += 1;
    if (score <= 1) return { score, label: '弱', color: 'bg-red-500' };
    if (score === 2) return { score, label: '中', color: 'bg-amber-500' };
    return { score, label: '强', color: 'bg-emerald-500' };
  }, [password]);

  const validate = (): string | null => {
    if (!USERNAME_RE.test(username.trim())) return '用户名需为 3-32 位，并以字母开头。';
    if (!PASSWORD_RE.test(password)) return '密码需为 8-64 位，且同时包含字母和数字。';
    if (password !== confirmPwd) return '两次输入的密码不一致。';
    if (!tenantId) return '请选择要加入的企业。';
    if (email.trim() && !EMAIL_RE.test(email.trim())) return '邮箱格式不正确。';
    if (phone.trim() && !PHONE_RE.test(phone.trim())) return '手机号格式不正确。';
    if (!agreed) return '请先阅读并同意《用户协议》和《隐私政策》。';
    return null;
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-br from-background via-background to-muted px-4 py-10">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -left-40 -top-40 h-96 w-96 rounded-full bg-foreground/[0.04] blur-3xl" />
        <div className="absolute -bottom-40 -right-40 h-96 w-96 rounded-full bg-foreground/[0.04] blur-3xl" />
      </div>

      <div className="relative z-10 w-full max-w-md">
        <div className="rounded-2xl border bg-card p-8 shadow-xl">
          <div className="mb-6 flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-foreground bg-background text-foreground shadow-sm">
              <Sparkles className="h-4 w-4" />
            </div>
            <span className="text-base font-semibold">
              ZWSK <span className="text-brand-gradient">Chat</span>
            </span>
          </div>

          <h2 className="text-2xl font-semibold tracking-tight">创建账号</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            选择要加入的企业，注册成功后将自动登录。
          </p>

          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const err = validate();
              if (err) {
                toast.error(err);
                return;
              }
              setLoading(true);
              try {
                const resp = await authApi.register({
                  username: username.trim(),
                  password,
                  nickname: nickname.trim() || undefined,
                  email: email.trim() || undefined,
                  phone: phone.trim() || undefined,
                  tenantId,
                });
                setSession(resp);
                toast.success(`注册成功，欢迎 ${resp.nickname || resp.username}`);
                navigate(next, { replace: true });
              } finally {
                setLoading(false);
              }
            }}
            className="mt-6 space-y-4"
          >
            <div className="space-y-2">
              <Label htmlFor="username">用户名 *</Label>
              <Input
                id="username"
                autoFocus
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="以字母开头，3-32 位"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">密码 *</Label>
              <div className="relative">
                <Input
                  id="password"
                  type={showPwd ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="8-64 位，至少包含字母和数字"
                />
                <button
                  type="button"
                  onClick={() => setShowPwd((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={showPwd ? '隐藏密码' : '显示密码'}
                >
                  {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {password ? (
                <div className="flex items-center gap-2 text-xs">
                  <div className="flex flex-1 gap-1">
                    {[1, 2, 3, 4].map((i) => (
                      <div
                        key={i}
                        className={`h-1 flex-1 rounded-full ${i <= pwdStrength.score ? pwdStrength.color : 'bg-muted'}`}
                      />
                    ))}
                  </div>
                  <span className="w-8 text-right text-muted-foreground">{pwdStrength.label}</span>
                </div>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPwd">确认密码 *</Label>
              <Input
                id="confirmPwd"
                type={showPwd ? 'text' : 'password'}
                autoComplete="new-password"
                value={confirmPwd}
                onChange={(e) => setConfirmPwd(e.target.value)}
                placeholder="再输入一次密码"
              />
              {confirmPwd && confirmPwd !== password ? (
                <p className="text-xs text-red-500">两次密码不一致</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="tenantId" className="flex items-center gap-1.5">
                <Building2 className="h-3.5 w-3.5" />
                加入企业 *
              </Label>
              <select
                id="tenantId"
                value={tenantId}
                onChange={(e) => setTenantId(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {tenants.length === 0 ? (
                  <option value="">暂无可加入企业，请联系平台管理员创建企业</option>
                ) : (
                  tenants.map((tenant) => (
                    <option key={tenant.id} value={tenant.id}>
                      {tenant.name} ({tenant.code})
                    </option>
                  ))
                )}
              </select>
              <p className="text-xs text-muted-foreground">
                注册后你将以普通成员身份加入该企业，企业管理员可后续调整角色和权限。
              </p>
            </div>

            {showAdvanced ? (
              <div className="space-y-4 rounded-xl border border-dashed bg-muted/30 p-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                    可选信息
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowAdvanced(false)}
                    className="text-xs text-muted-foreground hover:text-foreground"
                  >
                    收起
                  </button>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="nickname">昵称</Label>
                  <Input
                    id="nickname"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    placeholder="留空则使用用户名"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="email">邮箱</Label>
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="you@example.com"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="phone">手机号</Label>
                  <Input
                    id="phone"
                    type="tel"
                    autoComplete="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="11 位中国大陆手机号"
                  />
                </div>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => setShowAdvanced(true)}
                className="text-xs text-muted-foreground hover:text-foreground"
              >
                + 填写昵称 / 邮箱 / 手机
              </button>
            )}

            <Button type="submit" className="w-full" size="lg" disabled={loading}>
              <UserPlus className="mr-2 h-4 w-4" />
              {loading ? '注册中...' : '注册并登录'}
            </Button>

            <p className="text-center text-sm text-muted-foreground">
              已有账号？
              <Link
                to={`/login${next !== '/chat' ? `?next=${encodeURIComponent(next)}` : ''}`}
                className="ml-1 font-medium text-foreground hover:underline"
              >
                立即登录
              </Link>
            </p>
          </form>

          <div className="mt-6 border-t pt-4">
            <label className="flex items-start gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(e) => setAgreed(e.target.checked)}
                className="mt-0.5 h-4 w-4 shrink-0 rounded border-input accent-foreground"
              />
              <span className="text-xs text-muted-foreground leading-relaxed">
                我已阅读并同意{' '}
                <Link to="/terms" className="font-medium text-foreground hover:underline">《用户服务协议》</Link>
                {' '}和{' '}
                <Link to="/privacy" className="font-medium text-foreground hover:underline">《隐私政策》</Link>
                ，包括 AI 生成内容说明及个人信息收集与处理规则。如有疑问，请联系 privacy@noweyai.com。
              </span>
            </label>
          </div>
        </div>
      </div>
    </div>
  );
}
