import type { ElementType } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Bell,
  Bot,
  Boxes,
  Building2,
  CalendarDays,
  ClipboardList,
  FileDown,
  FileText,
  GitBranch,
  LayoutDashboard,
  MessageSquare,
  Send,
  Settings,
  ShieldCheck,
  Sparkles,
  UsersRound,
  X,
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/stores/auth';
import { useTenantFeatureStore } from '@/stores/tenantFeatures';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

interface NavItem {
  label: string;
  to: string;
  icon: ElementType;
  featureCode?: string;
  platformOnly?: boolean;
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const navGroups: NavGroup[] = [
  {
    title: 'AI 功能',
    items: [
      { label: 'AI 对话', to: '/chat', icon: MessageSquare, featureCode: 'ai_chat' },
      { label: '助手广场', to: '/assistants', icon: Bot, featureCode: 'ai_chat' },
      { label: '知识库', to: '/kb', icon: Sparkles, featureCode: 'knowledge_base' },
      { label: 'AI 动作中心', to: '/ai-actions', icon: Sparkles, featureCode: 'ai_actions' },
      { label: 'AI 风险看板', to: '/ai-risk-dashboard', icon: ShieldCheck, featureCode: 'tenant_dashboard' },
      { label: '语音诊断', to: '/voice-diagnostic', icon: Bot, featureCode: 'voice_diagnostic' },
    ],
  },
  {
    title: 'ERP 功能',
    items: [
      { label: '工作台', to: '/workbench', icon: LayoutDashboard, featureCode: 'workbench' },
      { label: '客户管理', to: '/crm/customers', icon: Building2, featureCode: 'crm_customers' },
      { label: '合同管理', to: '/contracts', icon: FileText, featureCode: 'contracts' },
      { label: '工单管理', to: '/work-orders', icon: ClipboardList, featureCode: 'work_orders' },
      { label: '工作派发', to: '/dispatch', icon: Send, featureCode: 'dispatch' },
      { label: '日程管理', to: '/schedule', icon: CalendarDays, featureCode: 'schedule' },
      { label: '审批流程', to: '/workflow', icon: GitBranch, featureCode: 'workflow' },
      { label: '通知中心', to: '/notifications', icon: Bell, featureCode: 'notifications' },
      { label: '导出中心', to: '/exports', icon: FileDown },
    ],
  },
  {
    title: '管理功能',
    items: [
      { label: '模板中心', to: '/templates', icon: Boxes, featureCode: 'template_center' },
      { label: '租户控制台', to: '/tenant-admin', icon: ShieldCheck, featureCode: 'tenant_console' },
      { label: '平台总览', to: '/platform/dashboard', icon: LayoutDashboard, platformOnly: true },
      { label: '部门管理', to: '/platform/departments', icon: UsersRound, platformOnly: true },
      { label: '租户管理', to: '/tenants', icon: Building2, platformOnly: true },
      { label: '系统设置', to: '/settings', icon: Settings },
    ],
  },
];

function isPlatformRole(roles: string[]) {
  return roles.some((role) => {
    const normalized = role.toUpperCase();
    return normalized.includes('PLATFORM') || normalized.includes('ADMIN') || normalized.includes('SUPER');
  });
}

export function Sidebar({ open, onClose }: SidebarProps) {
  const roles = useAuthStore((state) => state.roles);
  const loadedTenantId = useTenantFeatureStore((state) => state.loadedTenantId);
  const isEnabled = useTenantFeatureStore((state) => state.isEnabled);
  const canSeePlatform = isPlatformRole(roles);

  const canShowItem = (item: NavItem) => {
    if (item.platformOnly && !canSeePlatform) {
      return false;
    }
    return !loadedTenantId || isEnabled(item.featureCode);
  };

  const visibleGroups = navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter(canShowItem),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <>
      <div
        className={cn(
          'fixed inset-0 z-30 bg-background/70 backdrop-blur-sm transition-opacity md:hidden',
          open ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={onClose}
      />

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-72 max-w-[86vw] flex-col border-r bg-background transition-transform md:static md:z-auto md:w-64 md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-14 shrink-0 items-center gap-3 border-b px-4">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-xs font-bold text-primary-foreground">
            ZW
          </div>
          <div className="min-w-0 flex-1">
            <div className="truncate text-sm font-semibold">ZWSK Chat</div>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            onClick={onClose}
            aria-label="关闭侧边栏"
          >
            <X className="h-5 w-5" />
          </Button>
        </div>

        <nav className="min-h-0 flex-1 space-y-5 overflow-y-auto p-3">
          {visibleGroups.map((group) => (
            <section key={group.title} className="space-y-1">
              <div className="px-3 pb-1 text-xs font-semibold text-muted-foreground">
                {group.title}
              </div>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/workbench' || item.to === '/tenant-admin'}
                  onClick={onClose}
                  className={({ isActive }) =>
                    cn(
                      'flex min-h-10 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground',
                      isActive && 'bg-accent text-accent-foreground',
                    )
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" />
                  <span className="truncate">{item.label}</span>
                </NavLink>
              ))}
            </section>
          ))}
        </nav>
      </aside>
    </>
  );
}
