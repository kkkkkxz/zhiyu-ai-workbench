import { CheckCircle2, Clock, Loader2, XCircle } from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import type { ParseStatus } from '@/types/api';

const MAP: Record<
  ParseStatus,
  {
    variant: 'default' | 'secondary' | 'success' | 'warning' | 'destructive';
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    spin?: boolean;
  }
> = {
  PENDING: { variant: 'secondary', label: '排队中', icon: Clock },
  PARSING: { variant: 'warning', label: '解析中', icon: Loader2, spin: true },
  SUCCESS: { variant: 'success', label: '已完成', icon: CheckCircle2 },
  FAILED: { variant: 'destructive', label: '失败', icon: XCircle },
};

export function ParseStatusBadge({ status }: { status: ParseStatus }) {
  const item = MAP[status] || MAP.PENDING;
  const Icon = item.icon;
  return (
    <Badge variant={item.variant} className="gap-1">
      <Icon className={`h-3 w-3 ${item.spin ? 'animate-spin' : ''}`} />
      {item.label}
    </Badge>
  );
}
