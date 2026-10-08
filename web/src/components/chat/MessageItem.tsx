import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Bot, User, FileDown, Sparkles } from 'lucide-react';
import { useState } from 'react';
import { toast } from 'sonner';

import { cn } from '@/lib/utils';
import { Avatar } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { biddingApi } from '@/api/bidding';
import { References } from './References';
import { DataTable } from './DataTable';
import { SimpleChart } from './SimpleChart';
import { FeedbackWidget } from './FeedbackWidget';
import { DispatchConfirm } from './DispatchConfirm';
import { BizActionCard } from './BizActionCard';
import type { ReferenceItem } from '@/types/api';
import type { DispatchPreview } from '@/api/aiDispatch';
import type { BizActionResult } from '@/api/aiBizAction';

export interface DataQueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  sql?: string;
  executionMs?: number;
}

export interface ChartData {
  type: 'bar' | 'line';
  title?: string;
  data: { label: string; value: number }[];
}

export interface ChatTurn {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  references?: ReferenceItem[];
  pending?: boolean;
  error?: string;
  latencyMs?: number;
  tokenIn?: number;
  tokenOut?: number;
  createTime?: string;
  dataTable?: DataQueryResult;
  chart?: ChartData;
  dispatchPreview?: DispatchPreview;
  bizAction?: BizActionResult;
}

interface MessageItemProps {
  turn: ChatTurn;
}

export function MessageItem({ turn }: MessageItemProps) {
  const isUser = turn.role === 'user';
  const [exporting, setExporting] = useState(false);

  // 智能标书判定：如果是非用户消息，且内容包含 markdown 大纲结构（# 或 ##）以及与工程/招标/监理等行业关键词相关的词
  const isBiddingDoc = !isUser && turn.content && 
    (turn.content.includes('# ') || turn.content.includes('## ')) &&
    (turn.content.includes('标书') || turn.content.includes('招标') || turn.content.includes('监理') || turn.content.includes('兴天') || turn.content.includes('造价'));

  // 提取首个标题作为项目名称
  const getBiddingTitle = () => {
    if (!turn.content) return '未命名项目';
    const firstTitleLine = turn.content.split('\n').find(line => line.trim().startsWith('# ') || line.trim().startsWith('## '));
    if (firstTitleLine) {
      return firstTitleLine.replace(/[#\s]/g, '');
    }
    return '工程监理或招标代理项目';
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const title = getBiddingTitle();
      await biddingApi.exportWord({
        title: title,
        scope: '全过程工程咨询、项目监理与工程造价成果编制',
        budget: '详见标书详情段落',
        content: turn.content,
      });
      toast.success('标书 Word 文档导出成功！');
    } catch (e) {
      console.error(e);
      toast.error('导出失败，请重试');
    } finally {
      setExporting(false);
    }
  };

  return (
    <div
      className={cn(
        'flex w-full gap-3 px-4 py-4',
        isUser ? 'justify-end' : 'justify-start',
      )}
    >
      {!isUser && (
        <Avatar className="h-8 w-8 shrink-0 bg-foreground text-background">
          <span className="flex h-full w-full items-center justify-center">
            <Bot className="h-4 w-4" />
          </span>
        </Avatar>
      )}

      <div
        className={cn(
          'flex max-w-[min(82ch,calc(100%-3rem))] flex-col gap-2',
          isUser ? 'items-end' : 'items-start',
        )}
      >
        <div
          className={cn(
            'rounded-2xl px-4 py-3 text-sm leading-7 shadow-sm backdrop-blur-md',
            isUser
              ? 'bg-primary text-primary-foreground'
              : 'border bg-card/90 text-card-foreground',
            turn.error && 'border-destructive bg-destructive/10 text-destructive',
          )}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap break-words">{turn.content}</p>
          ) : (
            <div className="prose-chat min-w-0 break-words">
              {turn.content ? (
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {turn.content}
                </ReactMarkdown>
              ) : turn.pending ? (
                <span className="inline-flex items-center gap-2 text-muted-foreground">
                  <span className="inline-block h-2 w-2 animate-pulse-soft rounded-full bg-current" />
                  思考中…
                </span>
              ) : null}
              {turn.error && (
                <p className="text-destructive">⚠ {turn.error}</p>
              )}
            </div>
          )}
        </div>

        {!isUser && turn.references && turn.references.length > 0 && (
          <References items={turn.references} />
        )}

        {isBiddingDoc && (
          <Card className="border-red-500/20 bg-red-500/5 max-w-[min(82ch,calc(100%-3rem))] w-full">
            <CardContent className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center">
              <Sparkles className="h-6 w-6 shrink-0 text-red-600 animate-pulse" />
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-red-950">四川兴天建设项目管理标书生成助手</div>
                <div className="text-xs text-red-900/70 mt-0.5">
                  已匹配兴天项目管理、监理日志或工程造价大纲规范。您可以一键导出精美排版的 Microsoft Word 成果文档。
                </div>
              </div>
              <Button 
                onClick={handleExport} 
                disabled={exporting}
                size="sm"
                className="bg-red-600 text-white hover:bg-red-700 shrink-0 gap-1.5 shadow-sm"
              >
                <FileDown className="h-4 w-4" />
                {exporting ? '正在生成...' : '导出 Word 标书'}
              </Button>
            </CardContent>
          </Card>
        )}

        {!isUser && turn.dispatchPreview && (
          <DispatchConfirm preview={turn.dispatchPreview} />
        )}

        {!isUser && turn.bizAction && (
          <BizActionCard result={turn.bizAction} />
        )}

        {!isUser && turn.dataTable && (
          <DataTable
            columns={turn.dataTable.columns}
            rows={turn.dataTable.rows}
            sql={turn.dataTable.sql}
            executionMs={turn.dataTable.executionMs}
          />
        )}

        {!isUser && turn.chart && (
          <SimpleChart type={turn.chart.type} data={turn.chart.data} title={turn.chart.title} />
        )}

        {!isUser && !turn.pending && turn.content && (
          <div className="flex items-center gap-2">
            <p className="text-[10px] text-muted-foreground/70">AI 生成内容，仅供参考</p>
          </div>
        )}

        {!isUser && !turn.pending && turn.content && (
          <FeedbackWidget messageId={turn.id} />
        )}

        {!isUser && (turn.latencyMs || turn.tokenOut) ? (
          <div className="flex gap-3 text-[10px] uppercase tracking-wider text-muted-foreground">
            {turn.latencyMs ? <span>{turn.latencyMs} ms</span> : null}
            {turn.tokenIn ? <span>in {turn.tokenIn}</span> : null}
            {turn.tokenOut ? <span>out {turn.tokenOut}</span> : null}
          </div>
        ) : null}
      </div>

      {isUser && (
        <Avatar className="h-8 w-8 shrink-0 bg-muted-foreground text-background">
          <span className="flex h-full w-full items-center justify-center">
            <User className="h-4 w-4" />
          </span>
        </Avatar>
      )}
    </div>
  );
}
