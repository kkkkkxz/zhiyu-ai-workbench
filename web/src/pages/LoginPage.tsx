import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Bot, Building2, Eye, EyeOff, Radio, Sparkles } from 'lucide-react';
import { toast } from 'sonner';

import { authApi } from '@/api/auth';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuthStore } from '@/stores/auth';

const CODE_TENANT_CODE_REQUIRED = 3003;

export default function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = params.get('next') || '/chat';
  const setSession = useAuthStore((s) => s.setSession);
  const isAuthed = useAuthStore((s) => s.isAuthenticated());
  const roles = useAuthStore((s) => s.roles);
  const isTenantAdminOnly = roles.includes('TENANT_ADMIN') && !roles.includes('SUPER_ADMIN');

  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');
  const [tenantCode, setTenantCode] = useState('');
  const [showTenantCode, setShowTenantCode] = useState(false);
  const [showPwd, setShowPwd] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (isAuthed) {
      navigate(
        next === '/chat' && isTenantAdminOnly ? '/tenant-admin' : next === '/chat' ? '/workbench' : next,
        { replace: true },
      );
    }
  }, [isAuthed, isTenantAdminOnly, navigate, next]);

  const loginAndNavigate = async () => {
    if (!username.trim() || !password) {
      toast.error('请输入用户名和密码');
      return;
    }

    setLoading(true);
    try {
      const resp = await authApi.login({
        username: username.trim(),
        password,
        tenantCode: tenantCode.trim() ? tenantCode.trim() : undefined,
      });
      setSession(resp);
      toast.success(`欢迎回来，${resp.nickname || resp.username}`);
      navigate(
        next === '/chat' && isTenantAdminOnly ? '/tenant-admin' : next === '/chat' ? '/workbench' : next,
        { replace: true },
      );
    } catch (err) {
      const code = (err as { code?: number })?.code;
      if (code === CODE_TENANT_CODE_REQUIRED) {
        setShowTenantCode(true);
        setTimeout(() => document.getElementById('tenantCode')?.focus(), 0);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-gradient-to-br from-background via-background to-muted px-4">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute -left-40 -top-40 h-96 w-96 rounded-full bg-foreground/[0.04] blur-3xl" />
        <div className="absolute -bottom-40 -right-40 h-96 w-96 rounded-full bg-foreground/[0.04] blur-3xl" />
      </div>

      <div className="relative z-10 grid w-full max-w-5xl grid-cols-1 gap-10 lg:grid-cols-2">
        <div className="hidden flex-col justify-between rounded-2xl border bg-card/40 p-10 backdrop-blur-md lg:flex">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-foreground bg-background text-foreground shadow-sm">
              <Sparkles className="h-5 w-5" />
            </div>
            <div>
              <p className="text-xs uppercase tracking-wider text-muted-foreground">ZWSK Chat</p>
              <p className="text-lg font-bold tracking-tight">
                ZWSK <span className="text-brand-gradient">Chat</span>
              </p>
            </div>
          </div>

          <div className="space-y-3">
            <h1 className="text-3xl font-semibold tracking-tight lg:text-4xl">企业级 RAG 智能助手</h1>
            <p className="text-muted-foreground">知识库管理、流式问答、多租户安全、全链路审计。</p>
            <button
              type="button"
              onClick={() => navigate('/voice-ai')}
              className="group mt-6 flex w-full items-center gap-4 rounded-2xl border border-cyan-300/30 bg-slate-950 px-5 py-4 text-left text-white shadow-[0_0_40px_rgba(34,211,238,0.18)] transition hover:-translate-y-0.5 hover:border-cyan-300/70 hover:shadow-[0_0_60px_rgba(34,211,238,0.28)]"
            >
              <span className="relative flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-cyan-300 text-slate-950">
                <span className="absolute inset-0 animate-ping rounded-2xl bg-cyan-300/60" />
                <Bot className="relative h-6 w-6" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-semibold">进入 AI 全局语音助手</span>
                <span className="mt-1 block text-xs text-cyan-100/75">无需登录，直接体验语音对话与知识问答。</span>
              </span>
              <Radio className="h-5 w-5 text-cyan-200 transition group-hover:scale-110" />
            </button>
          </div>

          <p className="text-xs text-muted-foreground">© {new Date().getFullYear()} ZWSK Chat</p>
        </div>

        <div className="flex flex-col justify-center">
          <div className="rounded-2xl border bg-card p-8 shadow-xl">
            <div className="mb-6 lg:hidden">
              <div className="flex items-center gap-2">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg border border-foreground bg-background text-foreground shadow-sm">
                  <Sparkles className="h-4 w-4" />
                </div>
                <span className="text-base font-semibold">
                  ZWSK <span className="text-brand-gradient">Chat</span>
                </span>
              </div>
            </div>

            <h2 className="text-2xl font-semibold tracking-tight">欢迎回来</h2>
            <p className="mt-1 text-sm text-muted-foreground">使用您的企业账号登录控制台。</p>

            <form
              onSubmit={async (e) => {
                e.preventDefault();
                await loginAndNavigate();
              }}
              className="mt-6 space-y-4"
            >
              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  id="username"
                  autoFocus
                  autoComplete="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin"
                />
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">密码</Label>
                  <Link
                    to="#"
                    className="text-xs text-muted-foreground hover:text-foreground"
                    onClick={(e) => {
                      e.preventDefault();
                      toast.info('请联系管理员重置密码');
                    }}
                  >
                    忘记密码？
                  </Link>
                </div>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPwd ? 'text' : 'password'}
                    autoComplete="current-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="请输入密码"
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
              </div>

              {showTenantCode ? (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="tenantCode" className="flex items-center gap-1.5">
                      <Building2 className="h-3.5 w-3.5" />
                      租户编码
                    </Label>
                    <button
                      type="button"
                      className="text-xs text-muted-foreground hover:text-foreground"
                      onClick={() => {
                        setShowTenantCode(false);
                        setTenantCode('');
                      }}
                    >
                      取消租户编码
                    </button>
                  </div>
                  <Input
                    id="tenantCode"
                    autoComplete="organization"
                    value={tenantCode}
                    onChange={(e) => setTenantCode(e.target.value)}
                    placeholder="例如：zwsk"
                  />
                  <p className="text-xs text-muted-foreground">当同一用户名存在于多个租户时，请填写租户编码。</p>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => setShowTenantCode(true)}
                  className="-mt-1 flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
                >
                  <Building2 className="h-3.5 w-3.5" />
                  切换企业账号
                </button>
              )}

              <Button type="submit" className="w-full" size="lg" disabled={loading}>
                {loading ? '登录中...' : '登录'}
              </Button>
              <Button
                type="button"
                variant="outline"
                className="h-12 w-full border-cyan-500/40 bg-slate-950 text-cyan-50 shadow-[0_0_28px_rgba(34,211,238,0.16)] hover:bg-slate-900"
                onClick={() => navigate('/voice-ai')}
              >
                <Bot className="mr-2 h-4 w-4" />
                免登录进入 AI 语音
              </Button>

              <p className="text-center text-sm text-muted-foreground">
                还没有账号？
                <Link
                  to={`/register${next !== '/chat' ? `?next=${encodeURIComponent(next)}` : ''}`}
                  className="ml-1 font-medium text-foreground hover:underline"
                >
                  立即注册
                </Link>
              </p>
            </form>

            <p className="mt-6 text-center text-xs text-muted-foreground">
              登录即代表同意{' '}
              <Link to="/terms" className="font-medium hover:underline">《用户服务协议》</Link>
              {' '}和{' '}
              <Link to="/privacy" className="font-medium hover:underline">《隐私政策》</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
