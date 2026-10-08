/** 格式化字节数 */
export function formatBytes(bytes: number | string | null | undefined): string {
  if (bytes == null || bytes === '') return '-';
  const n = typeof bytes === 'string' ? Number(bytes) : bytes;
  if (!Number.isFinite(n) || n < 0) return '-';
  if (n < 1024) return `${n} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = n / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v.toFixed(v >= 100 ? 0 : v >= 10 ? 1 : 2)} ${units[i]}`;
}

/** 格式化为本地时间，宽容处理多种输入 */
export function formatDateTime(s: string | number | Date | null | undefined): string {
  if (!s) return '-';
  const d = s instanceof Date ? s : new Date(s as any);
  if (Number.isNaN(d.getTime())) return String(s);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** 简短相对时间 */
export function timeAgo(s: string | number | Date | null | undefined): string {
  if (!s) return '-';
  const d = s instanceof Date ? s : new Date(s as any);
  if (Number.isNaN(d.getTime())) return String(s);
  const diff = (Date.now() - d.getTime()) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)} 天前`;
  return formatDateTime(d);
}

/** 文件名取后缀（小写） */
export function fileExt(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
}

/** 截断字符串带省略号 */
export function truncate(s: string, n: number): string {
  if (!s) return '';
  return s.length > n ? s.slice(0, n) + '…' : s;
}
