import { Activity, CheckCircle2, Mic2, Radio, Wifi } from 'lucide-react';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

const checks = [
  { label: '麦克风权限', value: '浏览器授权后可用', icon: Mic2 },
  { label: '实时连接', value: '支持 WebSocket / SSE 链路', icon: Wifi },
  { label: '语音链路', value: 'ASR、LLM、TTS 可分段排查', icon: Radio },
];

export default function VoiceDiagnosticPage() {
  return (
    <div className="app-page-narrow">
      <div className="app-page-header">
        <h1 className="app-page-title">
          <Activity className="h-6 w-6" />
          语音诊断
        </h1>
        <Badge variant="outline">开发诊断</Badge>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>检查项目</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-3">
          {checks.map((item) => {
            const Icon = item.icon;
            return (
              <div key={item.label} className="rounded-lg border bg-muted/20 p-4">
                <Icon className="h-5 w-5 text-muted-foreground" />
                <div className="mt-3 font-medium">{item.label}</div>
                <div className="mt-1 text-sm text-muted-foreground">{item.value}</div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>移动端提示</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm text-muted-foreground">
          <p className="flex gap-2">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            手机浏览器需要 HTTPS 才能稳定调用麦克风，当前 Cloudflare Tunnel 已提供 HTTPS。
          </p>
          <p className="flex gap-2">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            如果无法录音，先检查系统麦克风权限，再刷新页面重新授权。
          </p>
          <p className="flex gap-2">
            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
            语音页面已按小屏幕优化，诊断页保持轻量布局，便于现场排查。
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
