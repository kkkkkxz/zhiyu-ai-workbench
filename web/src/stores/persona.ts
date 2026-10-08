import { create } from 'zustand';

import { getStoredPersonaId, storePersonaId } from '@/lib/personas';

interface PersonaState {
  personaId: string;
  setPersonaId: (personaId: string) => void;
}

export const usePersonaStore = create<PersonaState>()((set) => ({
  personaId: getStoredPersonaId(),
  setPersonaId: (personaId) => {
    const next = personaId || 'general';
    storePersonaId(next);
    set({ personaId: next });
  },
}));
