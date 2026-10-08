import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type ChatBackgroundSelection =
  | { type: 'default' }
  | { type: 'preset'; id: string }
  | { type: 'custom'; id: string };

export interface ChatBackgroundImage {
  id: string;
  name: string;
  dataUrl: string;
  createdAt: number;
}

export interface ChatBackgroundPreset {
  id: string;
  name: string;
  cssImage: string;
  previewClass: string;
  overlayClass: string;
}

interface ChatBackgroundState {
  selection: ChatBackgroundSelection;
  customImages: ChatBackgroundImage[];
  setDefault: () => void;
  setPreset: (id: string) => void;
  setCustom: (id: string) => void;
  addCustomImage: (image: ChatBackgroundImage) => void;
  removeCustomImage: (id: string) => void;
}

const STORAGE_KEY = 'zwsk.chatBackground';
const MAX_CUSTOM_IMAGES = 6;

export const CHAT_BACKGROUND_PRESETS: ChatBackgroundPreset[] = [
  {
    id: 'paper',
    name: '素纸',
    cssImage:
      'radial-gradient(circle at 16% 18%, rgba(15, 23, 42, 0.08), transparent 24%), linear-gradient(135deg, #f8fafc 0%, #eef2f7 48%, #ffffff 100%)',
    previewClass: 'bg-[linear-gradient(135deg,#f8fafc,#e2e8f0,#ffffff)]',
    overlayClass: 'bg-white/30',
  },
  {
    id: 'mist',
    name: '晨雾',
    cssImage:
      'radial-gradient(circle at 18% 20%, rgba(20, 184, 166, 0.18), transparent 30%), radial-gradient(circle at 76% 18%, rgba(59, 130, 246, 0.12), transparent 28%), linear-gradient(135deg, #f8fafc 0%, #e0f2fe 48%, #f0fdfa 100%)',
    previewClass: 'bg-[linear-gradient(135deg,#f8fafc,#bae6fd,#ccfbf1)]',
    overlayClass: 'bg-white/40',
  },
  {
    id: 'graphite',
    name: '石墨',
    cssImage:
      'linear-gradient(135deg, #111827 0%, #374151 48%, #f9fafb 100%)',
    previewClass: 'bg-[linear-gradient(135deg,#111827,#4b5563,#f9fafb)]',
    overlayClass: 'bg-white/50',
  },
  {
    id: 'linen',
    name: '亚麻',
    cssImage:
      'linear-gradient(90deg, rgba(15, 23, 42, 0.05) 1px, transparent 1px), linear-gradient(0deg, rgba(15, 23, 42, 0.04) 1px, transparent 1px), linear-gradient(135deg, #f7f4ed 0%, #e9eef2 52%, #ffffff 100%)',
    previewClass: 'bg-[linear-gradient(135deg,#f7f4ed,#e9eef2,#ffffff)]',
    overlayClass: 'bg-white/40',
  },
];

export const useChatBackgroundStore = create<ChatBackgroundState>()(
  persist(
    (set) => ({
      selection: { type: 'default' },
      customImages: [],
      setDefault: () => set({ selection: { type: 'default' } }),
      setPreset: (id) => set({ selection: { type: 'preset', id } }),
      setCustom: (id) => set({ selection: { type: 'custom', id } }),
      addCustomImage: (image) =>
        set((state) => ({
          selection: { type: 'custom', id: image.id },
          customImages: [
            image,
            ...state.customImages.filter((item) => item.id !== image.id),
          ].slice(0, MAX_CUSTOM_IMAGES),
        })),
      removeCustomImage: (id) =>
        set((state) => {
          const customImages = state.customImages.filter((item) => item.id !== id);
          const selection =
            state.selection.type === 'custom' && state.selection.id === id
              ? ({ type: 'default' } as const)
              : state.selection;
          return { customImages, selection };
        }),
    }),
    { name: STORAGE_KEY },
  ),
);

export function getActiveChatBackground(
  selection: ChatBackgroundSelection,
  customImages: ChatBackgroundImage[],
) {
  if (selection.type === 'preset') {
    return CHAT_BACKGROUND_PRESETS.find((item) => item.id === selection.id) ?? null;
  }
  if (selection.type === 'custom') {
    const image = customImages.find((item) => item.id === selection.id);
    if (!image) return null;
    return {
      id: image.id,
      name: image.name,
      cssImage: `url("${image.dataUrl}")`,
      overlayClass: 'bg-white/40',
    };
  }
  return null;
}
