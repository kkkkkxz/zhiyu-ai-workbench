import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function NotFoundPage() {
  return (
    <div className="flex h-full min-h-screen flex-col items-center justify-center gap-4 bg-background p-8 text-center">
      <p className="text-7xl font-extrabold tracking-tighter text-brand-gradient">
        404
      </p>
      <h1 className="text-2xl font-semibold">页面不存在</h1>
      <p className="max-w-sm text-sm text-muted-foreground">
        你访问的链接已经失效，或者你没有权限查看该资源。
      </p>
      <Button asChild>
        <Link to="/chat">
          <ArrowLeft className="h-4 w-4" /> 返回首页
        </Link>
      </Button>
    </div>
  );
}
