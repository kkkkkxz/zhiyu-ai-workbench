import { http } from '@/lib/api';

export type BizActionType =
  | 'DISPATCH'
  | 'CREATE_CUSTOMER'
  | 'CREATE_CONTRACT'
  | 'CREATE_WORKORDER'
  | 'CREATE_SCHEDULE'
  | 'START_APPROVAL'
  | 'QUERY_CUSTOMER'
  | 'QUERY_CONTRACT'
  | 'QUERY_WORKORDER'
  | 'QUERY_SCHEDULE'
  | 'ADD_FOLLOW_UP'
  | 'UNKNOWN';

export interface BizActionResult {
  actionType: BizActionType;
  needConfirm?: boolean;
  confirmMessage?: string;
  previewData?: Record<string, unknown>;
  queryResult?: boolean;
  summary?: string;
  columns?: string[];
  rows?: Record<string, unknown>[];
  executed?: boolean;
  successMessage?: string;
}

export const aiBizActionApi = {
  preview: (input: string) =>
    http.post<BizActionResult>('/ai-action/preview', { input }),
  confirm: (actionType: BizActionType, previewData: Record<string, unknown>) =>
    http.post<BizActionResult>('/ai-action/confirm', { actionType, previewData }),
};
