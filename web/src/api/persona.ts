import { http } from '@/lib/api';
import type { Persona } from '@/types/api';

export const personaApi = {
  list: (options?: { silentAuth?: boolean }) =>
    http.get<Persona[]>('/personas', {
      skipErrorToast: true,
      skipAuthRedirect: options?.silentAuth,
    } as any),
};
