import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ThemePreference = 'light' | 'dark' | 'system';

interface ThemeState {
  preference: ThemePreference;
  setPreference: (p: ThemePreference) => void;
  /** 把当前 preference 解析为实际 light/dark 并应用到 <html class>. */
  applyResolved: () => 'light' | 'dark';
}

const STORAGE_KEY = 'zwsk.theme';

function applyLight() {
  document.documentElement.classList.remove('dark');
}

/**
 * 全局锁定 light 主题。preference 仅作为占位保留接口，
 * setPreference / applyResolved 都不会再切换到 dark。
 */
export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      preference: 'light',
      setPreference: (_p) => {
        set({ preference: 'light' });
        applyLight();
      },
      applyResolved: () => {
        applyLight();
        return 'light';
      },
    }),
    { name: STORAGE_KEY },
  ),
);
