import { http } from '@/lib/api';

export interface TemplatePackage {
  id: string;
  code: string;
  name: string;
  category: string;
  description?: string;
  scenario?: string;
  modulesJson?: string;
  aiAgentsJson?: string;
  status: number;
  sort: number;
}

export interface TemplateInstallRecord {
  id: string;
  tenantId: string;
  packageCode: string;
  packageName: string;
  version: string;
  status: string;
  installedBy: string;
  installedAt: string;
}

export const templateApi = {
  list: () => http.get<TemplatePackage[]>('/templates'),
  installed: () => http.get<TemplateInstallRecord[]>('/templates/installed'),
  install: (packageCode: string) =>
    http.post<TemplateInstallRecord>(`/templates/${packageCode}/install`),
};
