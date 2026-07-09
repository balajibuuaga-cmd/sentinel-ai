import { ArrowRight } from 'lucide-react';
import { iconMap } from './icons';
import type { ActivityItemData } from '../types/dashboard';

export default function ActivityFeed({ items }: { items: ActivityItemData[] }) {
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
          {items.map((item) => {
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
      <button className="activity-view-all">
        View All Activity <ArrowRight size={14} />
      </button>
    </div>
  );
}
