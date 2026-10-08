import { api } from '@/lib/api';

export interface BiddingExportRequest {
  title: string;
  scope?: string;
  budget?: string;
  content: string;
}

export const biddingApi = {
  exportWord: async (req: BiddingExportRequest) => {
    const response = await api.post('/bidding/export', req, {
      responseType: 'blob',
    });
    
    // 触发浏览器直接下载文件
    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    });
    
    // 解析 Content-Disposition 里的文件名（若解析不出则用默认名）
    let filename = `兴天智能标书成果_${req.title}.docx`;
    const cd = response.headers['content-disposition'];
    if (cd) {
      const match = cd.match(/filename\*=UTF-8''(.+)/i);
      if (match && match[1]) {
        filename = decodeURIComponent(match[1]);
      }
    }

    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    
    // 释放资源
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  },
};
