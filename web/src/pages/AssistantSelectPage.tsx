import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  BadgeCheck,
  BotMessageSquare,
  Check,
  ChevronRight,
  Search,
  Sparkles,
} from 'lucide-react';
import { toast } from 'sonner';

import { chatApi } from '@/api/chat';
import { personaApi } from '@/api/persona';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import {
  ASSISTANT_PERSONAS,
  FALLBACK_PERSONAS,
  getPersonaProfile,
  mergePersonaProfiles,
  storeSessionPersonaId,
} from '@/lib/personas';
import { usePersonaStore } from '@/stores/persona';

type LocationState = {
  from?: string;
};

export default function AssistantSelectPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const selectedId = usePersonaStore((s) => s.personaId);
  const setPersonaId = usePersonaStore((s) => s.setPersonaId);
  const [profiles, setProfiles] = useState(ASSISTANT_PERSONAS);
  const [query, setQuery] = useState('');
  const [selectingId, setSelectingId] = useState<string | null>(null);

  const from = (location.state as LocationState | null)?.from || '/chat';
  const selected = getPersonaProfile(
    selectedId,
    profiles.find((item) => item.id === selectedId),
  );

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const personas = await personaApi.list({ silentAuth: true });
        if (!cancelled) setProfiles(mergePersonaProfiles(personas?.length ? personas : FALLBACK_PERSONAS));
      } catch {
        if (!cancelled) setProfiles(ASSISTANT_PERSONAS);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    const text = query.trim().toLowerCase();
    if (!text) return profiles;
    return profiles.filter((persona) => {
      const haystack = [
        persona.name,
        persona.title,
        persona.description,
        persona.tone,
        persona.domain,
        persona.strengths.join(' '),
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(text);
    });
  }, [profiles, query]);

  const choose = async (id: string) => {
    if (selectingId) return;
    const profile = profiles.find((item) => item.id === id) ?? getPersonaProfile(id);
    setSelectingId(id);
    try {
      const nextSession = await chatApi.createSession({
        title: `${profile.name} · 新会话`,
        sessionType: 'TEXT',
      });
      setPersonaId(id);
      storeSessionPersonaId(nextSession.id, id);
      toast.success('已切换助手，并创建新会话');
      navigate(`/chat/${nextSession.id}`, { replace: true });
    } catch (err) {
      toast.error((err as Error)?.message || '创建新会话失败');
      setSelectingId(null);
    }
  };

  return (
    <div className="h-full overflow-y-auto bg-[linear-gradient(180deg,#f8fafc_0%,#ffffff_42%,#f1f5f9_100%)] text-slate-950">
      <div className="mx-auto flex min-h-full w-full max-w-7xl flex-col px-4 py-5 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between gap-3">
          <Button asChild variant="outline" size="sm" className="gap-1.5 bg-white/80">
            <Link to={from}>
              <ArrowLeft className="h-3.5 w-3.5" />
              返回
            </Link>
          </Button>
          <div className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white/80 px-3 py-1.5 text-xs text-slate-500 shadow-sm md:flex">
            <BadgeCheck className="h-3.5 w-3.5 text-emerald-600" />
            当前：{selected.name}
          </div>
        </div>

        <section className="grid gap-6 py-6 lg:grid-cols-[0.82fr_1.18fr] lg:items-end">
          <div>
            <div className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-600 shadow-sm">
              <BotMessageSquare className="h-3.5 w-3.5" />
              助手人格
            </div>
            <h1 className="mt-5 max-w-2xl text-3xl font-semibold tracking-tight text-slate-950 md:text-4xl">
              选择一个更贴近场景的回答助手
            </h1>
            <p className="mt-3 max-w-2xl text-sm leading-7 text-slate-600">
              以 AI 技术、RAG 知识库、私有化部署和行业应用为主的默认助手已准备好。切换后，聊天首页会直接进入对应助手界面。
            </p>
          </div>

          <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
            <div className="flex items-center gap-3">
              <div
                className={cn(
                  'flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-sm font-semibold text-white shadow-sm',
                  selected.accent,
                )}
              >
                {selected.initials}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-xs font-medium text-slate-500">当前首页助手</p>
                <h2 className="truncate text-lg font-semibold text-slate-950">{selected.name}</h2>
                <p className="truncate text-sm text-slate-500">{selected.title}</p>
              </div>
            </div>
            <div className="mt-3 grid gap-2 sm:grid-cols-3">
              {selected.strengths.map((item) => (
                <div key={item} className="rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
                  {item}
                </div>
              ))}
            </div>
          </div>
        </section>

        <div className="mb-4 flex flex-col gap-3 border-y border-slate-200 bg-white/70 py-3 backdrop-blur md:flex-row md:items-center md:justify-between">
          <div className="flex min-w-0 items-center gap-2 text-sm text-slate-600">
            <Sparkles className="h-4 w-4 shrink-0 text-cyan-700" />
            <span className="truncate">共 {profiles.length} 个助手人格</span>
          </div>
          <label className="relative block w-full md:w-80">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索助手、场景或语气"
              className="h-9 bg-white pl-9"
            />
          </label>
        </div>

        <div className="grid gap-4 pb-8 md:grid-cols-2 xl:grid-cols-4">
          {filtered.map((persona) => {
            const active = persona.id === selectedId;
            return (
              <button
                key={persona.id}
                type="button"
                onClick={() => void choose(persona.id)}
                disabled={Boolean(selectingId)}
                className={cn(
                  'group flex min-h-[286px] flex-col rounded-lg border bg-white p-4 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-slate-400 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-900 focus-visible:ring-offset-2',
                  active && 'border-slate-950 shadow-md',
                  selectingId && 'cursor-wait opacity-70 hover:translate-y-0',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <div
                    className={cn(
                      'flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-xs font-semibold text-white shadow-sm',
                      persona.accent,
                    )}
                  >
                    {persona.initials}
                  </div>
                  <span
                    className={cn(
                      'flex h-6 items-center gap-1 rounded-full border px-2 text-[11px] font-medium',
                      active
                        ? 'border-slate-950 bg-slate-950 text-white'
                        : 'border-slate-200 text-slate-500 group-hover:border-slate-300',
                    )}
                  >
                    {active || selectingId === persona.id ? <Check className="h-3 w-3" /> : null}
                    {selectingId === persona.id ? '创建中' : active ? '已启用' : persona.domain}
                  </span>
                </div>

                <div className="mt-4">
                  <h3 className="text-lg font-semibold text-slate-950">{persona.name}</h3>
                  <p className="mt-1 text-sm font-medium text-slate-600">{persona.title}</p>
                  <p className="mt-3 line-clamp-3 text-sm leading-6 text-slate-500">
                    {persona.description}
                  </p>
                </div>

                <div className="mt-4 flex flex-wrap gap-1.5">
                  {persona.strengths.map((item) => (
                    <span
                      key={item}
                      className="rounded-md bg-slate-100 px-2 py-1 text-[11px] font-medium text-slate-600"
                    >
                      {item}
                    </span>
                  ))}
                </div>

                <div className="mt-auto pt-5">
                  <div className="flex items-center justify-between border-t border-slate-100 pt-3 text-xs text-slate-500">
                    <span className="truncate">{persona.tone}</span>
                    <ChevronRight className="h-4 w-4 shrink-0 transition-transform group-hover:translate-x-0.5" />
                  </div>
                </div>
              </button>
            );
          })}
        </div>

        {filtered.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
            没有匹配的助手人格
          </div>
        ) : null}
      </div>
    </div>
  );
}
