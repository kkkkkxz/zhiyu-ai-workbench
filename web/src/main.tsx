import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { Toaster } from 'sonner';

import { router } from './router';
import { useThemeStore } from './stores/theme';
import './index.css';

// 启动时把主题应用到 <html> 上，避免首屏闪烁
useThemeStore.getState().applyResolved();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouterProvider router={router} />
    {/*
      Toaster 视觉策略：
      - richColors：让 sonner 自动按 success/error/warning/info 上色，整个应用 toast 视觉一致
      - 不要再覆盖 toast 的 bg/text/border —— 历史教训：之前同时设了 richColors 和
        `bg-background text-foreground border-border`，两套样式打架，导致部分 toast 是彩色、
        部分是灰色，看起来"颜色不统一"。这里只对 description / 按钮做主题对齐，背景交给 sonner。
      - 调用约定：所有错误必须用 `toast.error(...)`；成功用 `toast.success(...)`；
        中性提示用 `toast.info(...)`；警告用 `toast.warning(...)`。**禁止裸调 `toast(...)`**。
    */}
    <Toaster
      position="top-center"
      richColors
      closeButton
      toastOptions={{
        classNames: {
          description: 'opacity-90',
          actionButton:
            'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
          cancelButton:
            'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        },
      }}
    />
  </React.StrictMode>,
);
