import { useState } from 'react';
import { ThumbsUp, ThumbsDown } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface FeedbackWidgetProps {
  messageId: string;
  onFeedback?: (messageId: string, rating: 'up' | 'down') => void;
}

export function FeedbackWidget({ messageId, onFeedback }: FeedbackWidgetProps) {
  const [rating, setRating] = useState<'up' | 'down' | null>(null);

  const handleClick = (r: 'up' | 'down') => {
    setRating(r);
    onFeedback?.(messageId, r);
  };

  return (
    <div className="flex items-center gap-1">
      <Button
        variant="ghost"
        size="icon"
        className={cn('h-6 w-6', rating === 'up' && 'text-green-600 bg-green-50')}
        onClick={() => handleClick('up')}
        disabled={rating !== null}
      >
        <ThumbsUp className="h-3 w-3" />
      </Button>
      <Button
        variant="ghost"
        size="icon"
        className={cn('h-6 w-6', rating === 'down' && 'text-red-600 bg-red-50')}
        onClick={() => handleClick('down')}
        disabled={rating !== null}
      >
        <ThumbsDown className="h-3 w-3" />
      </Button>
      {rating && <span className="text-[10px] text-muted-foreground ml-1">感谢反馈</span>}
    </div>
  );
}
