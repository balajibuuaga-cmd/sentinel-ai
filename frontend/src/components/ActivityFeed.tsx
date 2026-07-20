import { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { iconMap } from './icons';
import type { ActivityItemData } from '../types/dashboard';

const COLLAPSED_COUNT = 8;

export default function ActivityFeed({ items }: { items: ActivityItemData[] }) {
  const [expanded, setExpanded] = useState(false);
  const visible = expanded ? items : items.slice(0, COLLAPSED_COUNT);
  const hasMore = items.length > COLLAPSED_COUNT;

  return (
    <div className="panel activity-panel">
      <div className="activity-header">
        <span>Recent Activity Feed</span>
        <span className="live-badge">
          <span className="live-dot" /> Live
        </span>
      </div>
      {items.length === 0 ? (
        <div className="activity-empty">No activity recorded yet.</div>
      ) : (
        <ul className="activity-list">
          {visible.map((item) => {
            const Icon = iconMap[item.icon];
            return (
              <li key={item.id}>
                <div className={`activity-icon tone-${item.tone}`}>
                  <Icon size={14} />
                </div>
                <div className="activity-body">
                  <div className="activity-time">{item.time}</div>
                  <div className="activity-text">{item.text}</div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {hasMore ? (
        <button className="activity-view-all" onClick={() => setExpanded((value) => !value)}>
          {expanded ? (
            <>
              Show Less <ChevronUp size={14} />
            </>
          ) : (
            <>
              View All Activity ({items.length}) <ChevronDown size={14} />
            </>
          )}
        </button>
      ) : null}
    </div>
  );
}
