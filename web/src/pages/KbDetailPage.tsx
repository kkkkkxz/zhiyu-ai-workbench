import { useEffect, useState } from 'react';
import { ArrowLeft, FileText, RefreshCw, Trash2 } from 'lucide-react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';

import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { DocUpload } from '@/components/kb/DocUpload';
import { ParseStatusBadge } from '@/components/kb/ParseStatusBadge';
import { kbApi } from '@/api/kb';
import { docApi } from '@/api/doc';
import type { KnowledgeBase, KnowledgeDocument } from '@/types/api';
import { formatBytes } from '@/lib/format';

export default function KbDetailPage() {
  const { kbId } = useParams();
  const navigate = useNavigate();
  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [docs, setDocs] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    if (!kbId) return;
    setLoading(true);
    try {
      const [kbInfo, docList] = await Promise.all([kbApi.get(kbId), docApi.list(kbId)]);
      setKb(kbInfo);
      setDocs(docList ?? []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, [kbId]);

  if (!kbId) {
    return <div className="app-page">知识库不存在</div>;
  }

  return (
    <div className="app-page-narrow">
      <div className="app-page-header">
        <div className="min-w-0">
          <Button variant="ghost" className="mb-2 px-0" onClick={() => navigate('/kb')}>
            <ArrowLeft className="mr-1 h-4 w-4" />
            返回知识库
          </Button>
          <h1 className="app-page-title">{kb?.name || '知识库详情'}</h1>
          {kb?.description ? (
            <p className="mt-1 text-sm text-muted-foreground">{kb.description}</p>
          ) : null}
        </div>
        <div className="app-page-actions">
          <Button variant="outline" onClick={() => void load()}>
            <RefreshCw className="mr-1 h-4 w-4" />
            刷新
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">上传文档</CardTitle>
        </CardHeader>
        <CardContent>
          <DocUpload kbId={kbId} onUploaded={(doc) => setDocs((prev) => [doc, ...prev])} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">文档列表</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-10 text-center text-sm text-muted-foreground">加载中...</div>
          ) : docs.length === 0 ? (
            <div className="py-10 text-center text-sm text-muted-foreground">暂无文档</div>
          ) : (
            <div className="grid gap-3">
              {docs.map((doc) => (
                <div key={doc.id} className="app-card-row rounded-lg border p-3">
                  <div className="app-card-main">
                    <div className="app-card-title-line">
                      <FileText className="h-4 w-4 shrink-0 text-muted-foreground" />
                      <span className="app-card-title">{doc.fileName}</span>
                      <ParseStatusBadge status={doc.parseStatus} />
                    </div>
                    <div className="app-card-meta">
                      <span>{doc.fileType || '未知类型'}</span>
                      <span>{formatBytes(doc.fileSize || 0)}</span>
                      <span>分片 {doc.chunkCount ?? 0}</span>
                    </div>
                    {doc.parseError ? (
                      <p className="text-sm text-destructive">{doc.parseError}</p>
                    ) : null}
                  </div>
                  <Button
                    className="w-full sm:w-auto"
                    variant="ghost"
                    onClick={async () => {
                      await docApi.remove(kbId, doc.id);
                      toast.success('已删除');
                      setDocs((prev) => prev.filter((item) => item.id !== doc.id));
                    }}
                  >
                    <Trash2 className="mr-1 h-4 w-4" />
                    删除
                  </Button>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {kb ? (
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <Badge variant="outline">可见范围：{kb.visibility}</Badge>
          <Badge variant="outline">状态：{kb.status === 1 ? '启用' : '停用'}</Badge>
        </div>
      ) : null}
    </div>
  );
}
