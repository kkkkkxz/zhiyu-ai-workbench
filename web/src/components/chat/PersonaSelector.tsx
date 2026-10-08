import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Check, ChevronDown, ChevronRight, UserRound } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { personaApi } from '@/api/persona';
import type { Persona } from '@/types/api';
import { cn } from '@/lib/utils';
import { FALLBACK_PERSONAS, getPersonaProfile } from '@/lib/personas';

interface PersonaSelectorProps {
  value: string;
  onChange?: (next: string) => void;
  dark?: boolean;
  loadRemote?: boolean;
  to?: string;
  compact?: boolean;
}

export function PersonaSelector({
  value,
  onChange,
  dark,
  loadRemote = true,
  to = '/assistants',
  compact,
}: PersonaSelectorProps) {
  const location = useLocation();
  const [list, setList] = useState<Persona[]>(FALLBACK_PERSONAS);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!loadRemote) return;
    let cancelled = false;
    const load = async () => {
      try {
        const personas = await personaApi.list({ silentAuth: true });
        if (!cancelled && personas?.length) setList(personas);
      } catch {
        if (!cancelled) setList(FALLBACK_PERSONAS);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [loadRemote]);

  const selected = useMemo(() => {
    const remote = list.find((item) => item.id === value);
    return getPersonaProfile(value, remote);
  }, [list, value]);

  if (onChange) {
    return (
      <div className="relative w-full">
        <button
          type="button"
          onClick={() => setOpen((next) => !next)}
          aria-expanded={open}
          className={cn(
            'flex h-9 w-full items-center justify-between gap-2 rounded-xl px-2.5 text-left outline-none transition focus-visible:ring-2 focus-visible:ring-ring',
            compact && 'h-6 gap-1 rounded-md px-1.5 lg:h-9 lg:gap-2 lg:rounded-xl lg:px-2.5',
            dark
              ? 'border border-cyan-300/30 bg-slate-950/85 text-cyan-50 shadow-[inset_0_0_14px_rgba(34,211,238,0.08)] hover:border-cyan-200/60 hover:bg-cyan-300/[0.055]'
              : 'border border-input bg-background text-foreground shadow-sm hover:bg-accent',
          )}
        >
          <span className={cn('flex min-w-0 items-center gap-2', compact && 'gap-1 lg:gap-2')}>
            <span
              className={cn(
                'flex h-5 w-5 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-[9px] font-semibold text-white shadow-sm',
                compact && 'h-4 w-4 rounded-md text-[7px] lg:h-5 lg:w-5 lg:rounded-lg lg:text-[9px]',
                selected.accent,
              )}
            >
              {selected.initials}
            </span>
            <span className={cn('min-w-0 truncate text-[12px] font-medium', compact && 'text-[9px] lg:text-[12px]')}>{selected.name}</span>
          </span>
          <ChevronDown
            className={cn(
              'h-3.5 w-3.5 shrink-0 transition-transform',
              compact && 'h-3 w-3 lg:h-3.5 lg:w-3.5',
              dark ? 'text-cyan-200/70' : 'text-muted-foreground',
              open && 'rotate-180',
            )}
          />
        </button>

        {open ? (
          <div
            className={cn(
              dark
                ? 'mt-2 max-h-56 overflow-y-auto rounded-xl p-1.5'
                : 'absolute left-0 right-0 top-full z-50 mt-2 max-h-72 overflow-y-auto rounded-xl p-1.5 shadow-xl',
              compact && dark && 'mt-1 max-h-28 rounded-md p-1 lg:mt-2 lg:max-h-56 lg:rounded-xl lg:p-1.5',
              dark
                ? 'border border-cyan-300/15 bg-slate-950/95 shadow-cyan-950/40'
                : 'border bg-popover text-popover-foreground',
            )}
          >
            <div className={cn('space-y-1', compact && 'space-y-0.5 lg:space-y-1')}>
              {list.map((persona) => {
                const profile = getPersonaProfile(persona.id, persona);
                const active = profile.id === selected.id;
                return (
                  <button
                    type="button"
                    key={profile.id}
                    onClick={() => {
                      onChange(profile.id);
                      setOpen(false);
                    }}
                    className={cn(
                      'flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left transition',
                      compact && 'gap-1 rounded-md px-1 py-1 lg:gap-2 lg:rounded-lg lg:px-2 lg:py-1.5',
                      dark
                        ? active
                          ? 'bg-cyan-300/12 text-cyan-50'
                          : 'text-slate-300 hover:bg-cyan-300/[0.07] hover:text-cyan-50'
                        : active
                          ? 'bg-accent text-accent-foreground'
                          : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                    )}
                  >
                    <span
                      className={cn(
                        'mt-0.5 flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full border',
                        compact && 'mt-0 flex h-2.5 w-2.5 lg:mt-0.5 lg:h-3.5 lg:w-3.5',
                        dark
                          ? active
                            ? 'border-cyan-200 bg-cyan-200 text-slate-950'
                            : 'border-cyan-300/25 text-transparent'
                          : active
                            ? 'border-foreground bg-foreground text-background'
                            : 'border-border text-transparent',
                      )}
                    >
                      <Check className={cn('h-2.5 w-2.5', compact && 'h-2 w-2 lg:h-2.5 lg:w-2.5')} />
                    </span>
                    <span className="min-w-0">
                      <span className={cn('block truncate text-[11px] font-semibold leading-4', compact && 'text-[7px] leading-3 lg:text-[11px] lg:leading-4')}>
                        {profile.name}
                      </span>
                      {profile.description ? (
                        <span
                          className={cn(
                            'block truncate text-[10px] leading-4',
                            compact && 'hidden lg:block lg:text-[10px] lg:leading-4',
                            dark ? 'text-slate-400' : 'text-muted-foreground',
                          )}
                        >
                          {profile.description}
                        </span>
                      ) : null}
                      {profile.tone ? (
                        <span
                          className={cn(
                            'block truncate text-[10px] leading-4',
                            compact && 'hidden lg:block lg:text-[10px] lg:leading-4',
                            dark ? 'text-cyan-100/55' : 'text-muted-foreground',
                          )}
                        >
                          {profile.tone}
                        </span>
                      ) : null}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        ) : null}
      </div>
    );
  }

  if (dark) {
    return (
      <Link
        to={to}
        state={{ from: location.pathname }}
        className="group flex h-9 w-full items-center justify-between gap-2 rounded-xl border border-cyan-300/30 bg-slate-950/85 px-2.5 text-left text-cyan-50 outline-none shadow-[inset_0_0_14px_rgba(34,211,238,0.08)] transition hover:border-cyan-200/60 hover:bg-cyan-300/[0.055] focus-visible:border-cyan-200"
      >
        <span className="flex min-w-0 items-center gap-2">
          <span
            className={cn(
              'flex h-5 w-5 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-[9px] font-semibold text-white shadow-sm',
              selected.accent,
            )}
          >
            {selected.initials}
          </span>
          <span className="min-w-0 truncate text-[12px] font-medium">{selected.name}</span>
        </span>
        <ChevronRight className="h-3.5 w-3.5 shrink-0 text-cyan-200/70 transition-transform group-hover:translate-x-0.5" />
      </Link>
    );
  }

  return (
    <Button asChild variant="outline" size="sm" className="h-8 gap-1.5">
      <Link to={to} state={{ from: location.pathname }}>
        <UserRound className="h-3.5 w-3.5" />
        <span className="max-w-[160px] truncate">{selected.name}</span>
        <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
      </Link>
    </Button>
  );
}

export { FALLBACK_PERSONAS };
