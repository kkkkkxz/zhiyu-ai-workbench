import * as React from 'react';
import { cn } from '@/lib/utils';

interface AvatarProps extends React.HTMLAttributes<HTMLDivElement> {
  fallback?: React.ReactNode;
}

const Avatar = React.forwardRef<HTMLDivElement, AvatarProps>(
  ({ className, fallback, children, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        'relative flex h-9 w-9 shrink-0 overflow-hidden rounded-full bg-muted text-sm font-medium',
        className,
      )}
      {...props}
    >
      {children ?? (
        <span className="flex h-full w-full items-center justify-center text-muted-foreground">
          {fallback}
        </span>
      )}
    </div>
  ),
);
Avatar.displayName = 'Avatar';

export { Avatar };
