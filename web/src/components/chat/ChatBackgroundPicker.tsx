import { useRef, useState } from 'react';
import {
  Check,
  Image,
  RotateCcw,
  Trash2,
  Upload,
  Wallpaper,
} from 'lucide-react';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import {
  CHAT_BACKGROUND_PRESETS,
  type ChatBackgroundImage,
  useChatBackgroundStore,
} from '@/stores/chatBackground';

const MAX_SOURCE_BYTES = 12 * 1024 * 1024;
const MAX_STORED_CHARS = 850_000;
const OUTPUT_TYPE = 'image/jpeg';

export function ChatBackgroundPicker() {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);

  const selection = useChatBackgroundStore((s) => s.selection);
  const customImages = useChatBackgroundStore((s) => s.customImages);
  const setDefault = useChatBackgroundStore((s) => s.setDefault);
  const setPreset = useChatBackgroundStore((s) => s.setPreset);
  const setCustom = useChatBackgroundStore((s) => s.setCustom);
  const addCustomImage = useChatBackgroundStore((s) => s.addCustomImage);
  const removeCustomImage = useChatBackgroundStore((s) => s.removeCustomImage);

  const onUpload = async (file?: File) => {
    if (!file) return;
    if (!file.type.startsWith('image/') || file.type === 'image/svg+xml') {
      toast.error('请选择 JPG、PNG、WebP 或 GIF 图片');
      return;
    }
    if (file.size > MAX_SOURCE_BYTES) {
      toast.error('图片不能超过 12MB');
      return;
    }

    setUploading(true);
    try {
      const image = await compressBackgroundImage(file);
      addCustomImage(image);
      toast.success('背景已上传并应用');
    } catch (err) {
      toast.error((err as Error).message || '背景上传失败');
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  };

  const isDefault = selection.type === 'default';

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="聊天背景"
          title="聊天背景"
          className="shrink-0 bg-background/80 backdrop-blur"
        >
          <Wallpaper className="h-4 w-4" />
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[min(760px,92vh)] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>聊天背景</DialogTitle>
          <DialogDescription>
            选择预设背景，或上传图片作为当前浏览器的聊天背景。
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-wrap items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp,image/gif"
            className="sr-only"
            onChange={(e) => void onUpload(e.target.files?.[0])}
          />
          <Button
            type="button"
            onClick={() => inputRef.current?.click()}
            loading={uploading}
          >
            <Upload className="h-4 w-4" />
            上传图片
          </Button>
          <Button type="button" variant="outline" onClick={setDefault}>
            <RotateCcw className="h-4 w-4" />
            恢复默认
          </Button>
        </div>

        <section className="space-y-3">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Image className="h-4 w-4" />
            预设
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <BackgroundChoice
              active={isDefault}
              name="默认"
              previewClass="bg-background"
              onClick={setDefault}
            />
            {CHAT_BACKGROUND_PRESETS.map((preset) => (
              <BackgroundChoice
                key={preset.id}
                active={selection.type === 'preset' && selection.id === preset.id}
                name={preset.name}
                previewClass={preset.previewClass}
                onClick={() => setPreset(preset.id)}
              />
            ))}
          </div>
        </section>

        <section className="space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Upload className="h-4 w-4" />
              我的上传
            </div>
            {customImages.length > 0 ? (
              <span className="text-xs text-muted-foreground">
                最多保留最近 6 张
              </span>
            ) : null}
          </div>

          {customImages.length === 0 ? (
            <div className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">
              还没有上传背景图片
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {customImages.map((item) => {
                const active =
                  selection.type === 'custom' && selection.id === item.id;
                return (
                  <div key={item.id} className="group relative">
                    <button
                      type="button"
                      onClick={() => setCustom(item.id)}
                      className={cn(
                        'relative block aspect-[4/3] w-full overflow-hidden rounded-lg border text-left transition',
                        active
                          ? 'border-primary ring-2 ring-primary/30'
                          : 'border-border hover:border-primary/50',
                      )}
                    >
                      <img
                        src={item.dataUrl}
                        alt={item.name}
                        className="h-full w-full object-cover"
                      />
                      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/65 to-transparent p-2">
                        <div className="truncate text-xs font-medium text-white">
                          {item.name}
                        </div>
                      </div>
                      {active ? <ActiveMark /> : null}
                    </button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="icon"
                      aria-label={`删除 ${item.name}`}
                      className="absolute right-2 top-2 h-7 w-7 opacity-0 shadow-sm transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
                      onClick={() => removeCustomImage(item.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </DialogContent>
    </Dialog>
  );
}

function BackgroundChoice({
  active,
  name,
  previewClass,
  onClick,
}: {
  active: boolean;
  name: string;
  previewClass: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'relative aspect-[4/3] overflow-hidden rounded-lg border text-left transition',
        active
          ? 'border-primary ring-2 ring-primary/30'
          : 'border-border hover:border-primary/50',
      )}
    >
      <div className={cn('h-full w-full', previewClass)} />
      <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 to-transparent p-2">
        <div className="text-xs font-medium text-white">{name}</div>
      </div>
      {active ? <ActiveMark /> : null}
    </button>
  );
}

function ActiveMark() {
  return (
    <span className="absolute right-2 top-2 inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-sm">
      <Check className="h-3.5 w-3.5" />
    </span>
  );
}

async function compressBackgroundImage(file: File): Promise<ChatBackgroundImage> {
  const image = await loadImage(file);
  let maxEdge = 1920;
  let quality = 0.86;
  let dataUrl = '';

  for (let attempt = 0; attempt < 5; attempt += 1) {
    dataUrl = renderImage(image, maxEdge, quality);
    if (dataUrl.length <= MAX_STORED_CHARS) break;
    maxEdge = Math.max(960, Math.round(maxEdge * 0.78));
    quality = Math.max(0.68, quality - 0.06);
  }

  if (dataUrl.length > MAX_STORED_CHARS) {
    throw new Error('图片压缩后仍然过大，请换一张更小的图片');
  }

  return {
    id: `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    name: file.name.replace(/\.[^.]+$/, '') || '自定义背景',
    dataUrl,
    createdAt: Date.now(),
  };
}

function loadImage(file: File): Promise<HTMLImageElement> {
  const url = URL.createObjectURL(file);
  return new Promise((resolve, reject) => {
    const image = new window.Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('图片无法读取'));
    };
    image.src = url;
  });
}

function renderImage(image: HTMLImageElement, maxEdge: number, quality: number) {
  const width = image.naturalWidth || image.width;
  const height = image.naturalHeight || image.height;
  if (!width || !height) throw new Error('图片尺寸无效');

  const scale = Math.min(1, maxEdge / Math.max(width, height));
  const targetWidth = Math.max(1, Math.round(width * scale));
  const targetHeight = Math.max(1, Math.round(height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('当前浏览器不支持图片压缩');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, targetWidth, targetHeight);
  ctx.drawImage(image, 0, 0, targetWidth, targetHeight);
  return canvas.toDataURL(OUTPUT_TYPE, quality);
}
