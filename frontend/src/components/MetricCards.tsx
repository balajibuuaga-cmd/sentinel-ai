import type { CSSProperties } from 'react';
import { AreaChart, Area, ResponsiveContainer } from 'recharts';
import { iconMap } from './icons';
import type { MetricCardData } from '../types/dashboard';

export default function MetricCards({ metrics }: { metrics: MetricCardData[] }) {
  return (
    <div className="metric-row">
      {metrics.map((card) => {
        const Icon = iconMap[card.icon];
        const sparkData = card.spark?.map((v, i) => ({ i, v })) ?? null;
        return (
          <div
            className="panel metric-card"
            key={card.id}
            style={{ '--accent': card.accent } as CSSProperties}
          >
            <div className="metric-card-top">
              <div className="metric-icon" style={{ background: `color-mix(in srgb, ${card.accent} 18%, transparent)`, color: card.accent }}>
                <Icon size={17} />
              </div>
              <div className="metric-label">{card.label}</div>
            </div>
            <div className="metric-value">{card.value}</div>
            <div className="metric-caption">{card.caption}</div>
            {sparkData ? (
              <div className="metric-spark">
                <ResponsiveContainer width="100%" height={36}>
                  <AreaChart data={sparkData} margin={{ top: 2, right: 0, left: 0, bottom: 0 }}>
                    <defs>
                      <linearGradient id={`spark-${card.id}`} x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={card.accent} stopOpacity={0.5} />
                        <stop offset="100%" stopColor={card.accent} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <Area
                      type="monotone"
                      dataKey="v"
                      stroke={card.accent}
                      strokeWidth={2}
                      fill={`url(#spark-${card.id})`}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
