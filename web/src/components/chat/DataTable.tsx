import { Download } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface DataTableProps {
  columns: string[];
  rows: Record<string, unknown>[];
  sql?: string;
  executionMs?: number;
}

export function DataTable({ columns, rows, sql, executionMs }: DataTableProps) {
  const exportCsv = () => {
    const header = columns.join(',');
    const body = rows.map(r => columns.map(c => JSON.stringify(r[c] ?? '')).join(',')).join('\n');
    const blob = new Blob([header + '\n' + body], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'query_result.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-2 my-2">
      {sql && (
        <details className="text-xs">
          <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
            SQL {executionMs && `(${executionMs}ms)`}
          </summary>
          <pre className="mt-1 rounded bg-muted p-2 text-xs overflow-x-auto">{sql}</pre>
        </details>
      )}
      <div className="overflow-x-auto rounded-lg border">
        <table className="w-full text-sm">
          <thead className="bg-muted/50">
            <tr>
              {columns.map(col => (
                <th key={col} className="whitespace-nowrap px-3 py-2 text-left font-medium text-muted-foreground">
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.slice(0, 100).map((row, i) => (
              <tr key={i} className="border-t hover:bg-muted/30">
                {columns.map(col => (
                  <td key={col} className="whitespace-nowrap px-3 py-1.5">
                    {String(row[col] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{rows.length} 行结果{rows.length > 100 && '（仅展示前 100 行）'}</span>
        <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={exportCsv}>
          <Download className="h-3 w-3 mr-1" /> 导出 CSV
        </Button>
      </div>
    </div>
  );
}
