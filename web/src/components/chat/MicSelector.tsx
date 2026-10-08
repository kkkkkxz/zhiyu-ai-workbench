import { useEffect, useState } from 'react';
import { Mic, Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { listAudioInputDevices, type AudioInputDevice } from '@/voice/v3/audio';
import { truncate } from '@/lib/format';

interface MicSelectorProps {
  /** 当前选中的输入设备 ID；空字符串/undefined = 系统默认 */
  value?: string;
  onChange: (deviceId: string | undefined) => void;
  /** 通话进行中应禁用切换，避免拔流 */
  disabled?: boolean;
}

/**
 * 麦克风输入设备选择器，外观与 `KbSelector` 保持一致（黑白简约 + DropdownMenu）。
 * 设备列表懒加载：菜单首次打开时才 enumerate，避免空跑权限。
 */
export function MicSelector({ value, onChange, disabled }: MicSelectorProps) {
  const [list, setList] = useState<AudioInputDevice[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    let alive = true;
    const load = async () => {
      setLoading(true);
      try {
        const devices = await listAudioInputDevices();
        if (!alive) return;
        setList(devices);
        // 选中的设备已被拔出 → 自动回退到系统默认
        if (value && !devices.some((d) => d.deviceId === value)) {
          onChange(undefined);
        }
      } catch {
        if (alive) setList([]);
      } finally {
        if (alive) setLoading(false);
      }
    };
    void load();
    const handler = () => void load();
    navigator.mediaDevices?.addEventListener?.('devicechange', handler);
    return () => {
      alive = false;
      navigator.mediaDevices?.removeEventListener?.('devicechange', handler);
    };
  }, [open, value, onChange]);

  const selected = list.find((d) => d.deviceId === value);
  const label = !value
    ? '默认麦克风'
    : selected
      ? truncate(selected.label, 16)
      : '默认麦克风';

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm" className="h-8 gap-1.5" disabled={disabled}>
          <Mic className="h-3.5 w-3.5" />
          <span className="max-w-[160px] truncate">{label}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72">
        <DropdownMenuLabel>选择麦克风</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {loading ? (
          <div className="flex items-center justify-center gap-2 py-4 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" /> 检测设备中
          </div>
        ) : (
          <DropdownMenuRadioGroup
            value={value ?? ''}
            onValueChange={(next) => onChange(next || undefined)}
          >
            <DropdownMenuRadioItem value="" onSelect={(e) => e.preventDefault()}>
              <div className="flex flex-col">
                <span className="font-medium">系统默认麦克风</span>
                <span className="text-[11px] text-muted-foreground">由操作系统选择当前输入设备</span>
              </div>
            </DropdownMenuRadioItem>
            {list.length === 0 ? (
              <div className="px-3 py-3 text-center text-xs text-muted-foreground">
                未检测到额外的输入设备
              </div>
            ) : (
              list.map((device) => (
                <DropdownMenuRadioItem
                  key={device.deviceId || device.label}
                  value={device.deviceId}
                  onSelect={(e) => e.preventDefault()}
                >
                  <span className="font-medium">{truncate(device.label, 30)}</span>
                </DropdownMenuRadioItem>
              ))
            )}
          </DropdownMenuRadioGroup>
        )}
        {disabled ? (
          <>
            <DropdownMenuSeparator />
            <p className="px-3 pb-2 pt-1 text-[11px] text-muted-foreground">
              通话进行中，请挂断后再切换麦克风
            </p>
          </>
        ) : null}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
