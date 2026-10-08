// 注意：本文件所有页面都被 JSX 中的 element={<X />} 直接引用，
// 不是直接调用。某些 organize-imports / ESLint 设置可能误判为未使用并删除——
// 历史上发生过 RegisterPage import 被自动清理导致整页白屏。维护时确保 import 行
// 数与 JSX 引用数一致。
import { createBrowserRouter, Navigate } from 'react-router-dom';

import App from './App';
import { ProtectedRoute } from '@/components/layout/ProtectedRoute';
import { AppShell } from '@/components/layout/AppShell';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import ChatPage from '@/pages/ChatPage';
import AssistantSelectPage from '@/pages/AssistantSelectPage';
import KbListPage from '@/pages/KbListPage';
import KbDetailPage from '@/pages/KbDetailPage';
import SettingsPage from '@/pages/SettingsPage';
import TenantManagementPage from '@/pages/TenantManagementPage';
import PlatformDashboardPage from '@/pages/PlatformDashboardPage';
import PlatformDeptPage from '@/pages/PlatformDeptPage';
import AdminApplyPage from '@/pages/AdminApplyPage';
import VoiceDiagnosticPage from '@/pages/VoiceDiagnosticPage';
import JarvisVoicePage from '@/pages/JarvisVoicePage';
import NotFoundPage from '@/pages/NotFoundPage';
import CustomerListPage from '@/pages/CustomerListPage';
import ContractListPage from '@/pages/ContractListPage';
import WorkOrderListPage from '@/pages/WorkOrderListPage';
import DispatchPage from '@/pages/DispatchPage';
import SchedulePage from '@/pages/SchedulePage';
import WorkflowPage from '@/pages/WorkflowPage';
import NotificationPage from '@/pages/NotificationPage';
import WorkbenchPage from '@/pages/WorkbenchPage';
import TenantAdminPage from '@/pages/TenantAdminPage';
import TemplateCenterPage from '@/pages/TemplateCenterPage';
import AiActionCenterPage from '@/pages/AiActionCenterPage';
import AiRiskDashboardPage from '@/pages/AiRiskDashboardPage';
import ExportCenterPage from '@/pages/ExportCenterPage';

export const router = createBrowserRouter([
  {
    element: <App />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/voice-ai', element: <JarvisVoicePage /> },
      { path: '/jarvis', element: <Navigate to="/voice-ai" replace /> },
      {
        path: '/tenant-admin',
        element: (
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <TenantAdminPage /> },
        ],
      },
      {
        element: (
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        ),
        children: [
          { index: true, element: <Navigate to="/workbench" replace /> },
          { path: 'workbench', element: <WorkbenchPage /> },
          { path: 'chat', element: <ChatPage /> },
          { path: 'chat/:sessionId', element: <ChatPage /> },
          { path: 'assistants', element: <AssistantSelectPage /> },
          { path: 'templates', element: <TemplateCenterPage /> },
          { path: 'ai-actions', element: <AiActionCenterPage /> },
          { path: 'ai-risk-dashboard', element: <AiRiskDashboardPage /> },
          { path: 'kb', element: <KbListPage /> },
          { path: 'kb/:kbId', element: <KbDetailPage /> },
          { path: 'voice', element: <Navigate to="/chat" replace /> },
          { path: 'voice-diagnostic', element: <VoiceDiagnosticPage /> },
          { path: 'tenants', element: <TenantManagementPage /> },
          { path: 'platform/dashboard', element: <PlatformDashboardPage /> },
          { path: 'platform/departments', element: <PlatformDeptPage /> },
          { path: 'platform/admin-applies', element: <AdminApplyPage /> },
          { path: 'crm/customers', element: <CustomerListPage /> },
          { path: 'contracts', element: <ContractListPage /> },
          { path: 'work-orders', element: <WorkOrderListPage /> },
          { path: 'dispatch', element: <DispatchPage /> },
          { path: 'schedule', element: <SchedulePage /> },
          { path: 'workflow', element: <WorkflowPage /> },
          { path: 'notifications', element: <NotificationPage /> },
          { path: 'exports', element: <ExportCenterPage /> },
          { path: 'settings', element: <SettingsPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
