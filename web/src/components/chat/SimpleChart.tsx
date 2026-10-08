interface SimpleChartProps {
  type: 'bar' | 'line';
  data: { label: string; value: number }[];
  title?: string;
}

export function SimpleChart({ type, data, title }: SimpleChartProps) {
  if (!data || data.length === 0) return null;
  const max = Math.max(...data.map(d => d.value), 1);

  return (
    <div className="my-2 rounded-lg border p-4 space-y-3">
      {title && <div className="text-sm font-medium">{title}</div>}
      {type === 'bar' ? (
        <div className="space-y-2">
          {data.map((d, i) => (
            <div key={i} className="flex items-center gap-2">
              <span className="text-xs text-muted-foreground w-24 truncate text-right">{d.label}</span>
              <div className="flex-1 h-5 bg-muted rounded overflow-hidden">
                <div
                  className="h-full bg-primary/80 rounded transition-all duration-500"
                  style={{ width: `${(d.value / max) * 100}%` }}
                />
              </div>
              <span className="text-xs font-mono w-16 text-right">{d.value.toLocaleString()}</span>
            </div>
          ))}
        </div>
      ) : (
        <svg viewBox={`0 0 ${data.length * 60} 120`} className="w-full h-32">
          {/* grid lines */}
          {[0, 0.25, 0.5, 0.75, 1].map(pct => (
            <line key={pct} x1="0" y1={110 - pct * 100} x2={data.length * 60} y2={110 - pct * 100}
              stroke="currentColor" strokeOpacity="0.1" />
          ))}
          {/* line */}
          <polyline
            fill="none"
            stroke="hsl(var(--primary))"
            strokeWidth="2"
            points={data.map((d, i) => `${i * 60 + 30},${110 - (d.value / max) * 100}`).join(' ')}
          />
          {/* dots + labels */}
          {data.map((d, i) => (
            <g key={i}>
              <circle cx={i * 60 + 30} cy={110 - (d.value / max) * 100} r="3" fill="hsl(var(--primary))" />
              <text x={i * 60 + 30} y="120" textAnchor="middle" fontSize="8" fill="currentColor" opacity="0.6">
                {d.label.slice(0, 6)}
              </text>
            </g>
          ))}
        </svg>
      )}
    </div>
  );
}
