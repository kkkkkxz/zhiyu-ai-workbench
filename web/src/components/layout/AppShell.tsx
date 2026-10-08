import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';

import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';
import { TooltipProvider } from '@/components/ui/tooltip';
import { useAuthStore } from '@/stores/auth';
import { useTenantFeatureStore } from '@/stores/tenantFeatures';

export function AppShell() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const tenantId = useAuthStore((s) => s.tenantId);
  const loadFeatures = useTenantFeatureStore((s) => s.load);

  useEffect(() => {
    void loadFeatures(tenantId);
  }, [loadFeatures, tenantId]);

  return (
    <TooltipProvider delayDuration={150}>
      <div className="flex h-full w-full min-w-0">
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        <div className="flex h-full min-w-0 flex-1 flex-col">
          <TopBar onMenu={() => setSidebarOpen(true)} />
          <main className="min-h-0 flex-1 overflow-y-auto">
            <Outlet />
          </main>
        </div>
      </div>
    </TooltipProvider>
  );
}
