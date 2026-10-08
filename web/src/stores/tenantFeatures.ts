import { create } from 'zustand';

import { tenantApi } from '@/api/tenant';
import type { TenantFeature } from '@/types/api';

interface TenantFeatureState {
  features: TenantFeature[];
  loadedTenantId: string | null;
  loading: boolean;
  enabledCodes: Set<string>;
  load: (tenantId?: string | null) => Promise<void>;
  refresh: (tenantId?: string | null) => Promise<void>;
  setFeature: (featureCode: string, enabled: boolean) => void;
  clear: () => void;
  isEnabled: (featureCode?: string) => boolean;
}

export const useTenantFeatureStore = create<TenantFeatureState>((set, get) => ({
  features: [],
  loadedTenantId: null,
  loading: false,
  enabledCodes: new Set<string>(),

  load: async (tenantId) => {
    if (!tenantId) return;
    const state = get();
    if (state.loadedTenantId === tenantId && state.features.length > 0) return;
    await get().refresh(tenantId);
  },

  refresh: async (tenantId) => {
    if (!tenantId) return;
    set({ loading: true });
    try {
      const features = await tenantApi.listCurrentFeatures();
      set({
        features,
        loadedTenantId: tenantId,
        enabledCodes: new Set(features.filter((item) => item.enabled).map((item) => item.code)),
      });
    } finally {
      set({ loading: false });
    }
  },

  setFeature: (featureCode, enabled) =>
    set((state) => {
      const enabledCodes = new Set(state.enabledCodes);
      if (enabled) {
        enabledCodes.add(featureCode);
      } else {
        enabledCodes.delete(featureCode);
      }
      return {
        enabledCodes,
        features: state.features.map((item) =>
          item.code === featureCode ? { ...item, enabled } : item,
        ),
      };
    }),

  clear: () =>
    set({
      features: [],
      loadedTenantId: null,
      loading: false,
      enabledCodes: new Set<string>(),
    }),

  isEnabled: (featureCode) => {
    if (!featureCode) return true;
    if (featureCode === 'workbench' || featureCode === 'tenant_console') {
      return true;
    }
    return get().enabledCodes.has(featureCode);
  },
}));
