import { LogOut, Menu, ShieldCheck, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

import { authApi } from '@/api/auth';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuthStore } from '@/stores/auth';

interface TopBarProps {
  onMenu: () => void;
}

export function TopBar({ onMenu }: TopBarProps) {
  const navigate = useNavigate();
  const username = useAuthStore((s) => s.username);
  const nickname = useAuthStore((s) => s.nickname);
  const roles = useAuthStore((s) => s.roles);
  const clear = useAuthStore((s) => s.clear);

  const display = nickname || username || '用户';
  const initial = display.slice(0, 1).toUpperCase();

  const onLogout = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore
    } finally {
      clear();
      navigate('/login', { replace: true });
    }
  };

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-background/80 px-4 backdrop-blur-md">
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        onClick={onMenu}
        aria-label="打开侧栏"
      >
        <Menu className="h-5 w-5" />
      </Button>

      <div className="flex flex-1 items-center gap-2 text-sm text-muted-foreground" />

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <button
            className="flex items-center gap-2 rounded-full p-1 text-sm transition-colors hover:bg-accent"
            aria-label="用户菜单"
          >
            <Avatar className="h-8 w-8 bg-foreground text-background">
              <span className="flex h-full w-full items-center justify-center font-semibold">
                {initial}
              </span>
            </Avatar>
            <span className="hidden text-sm font-medium md:inline">{display}</span>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-56">
          <DropdownMenuLabel>
            <div className="flex flex-col">
              <span className="text-sm font-semibold">{display}</span>
              <span className="text-xs text-muted-foreground">@{username}</span>
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator />
          {roles?.length ? (
            <DropdownMenuItem disabled>
              <ShieldCheck className="h-4 w-4" />
              <span className="text-xs">{roles.join(' / ')}</span>
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuItem onClick={() => navigate('/settings')}>
            <UserRound className="h-4 w-4" /> 个人设置
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={onLogout} className="text-destructive focus:text-destructive">
            <LogOut className="h-4 w-4" /> 退出登录
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
