import { Search, Bell, Zap, Settings, ShieldCheck, LogOut } from 'lucide-react';
import type { HeaderStatsData } from '../types/dashboard';

interface Props {
  header: HeaderStatsData;
  notificationCount: number;
  onLogout: () => void;
}

export default function TopBar({ header, notificationCount, onLogout }: Props) {
  const initials = header.fullName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  return (
    <header className="topbar">
      <div className="topbar-greeting">
        <h1>
          {header.timeGreeting}, {header.firstName} <span className="wave">👋</span>
        </h1>
        <p>
          Sentinel AI analyzed <span className="highlight">{header.organizationName}</span> in real time
        </p>
      </div>

      <div className="topbar-search">
        <Search size={16} />
        <input placeholder="Ask Sentinel anything..." />
        <kbd>⌘K</kbd>
      </div>

      <div className="topbar-actions">
        <button className="icon-pill" title="Compliance">
          <ShieldCheck size={16} />
        </button>
        <button className="icon-pill" title="Notifications">
          <Bell size={16} />
          {notificationCount > 0 ? <span className="dot-badge">{notificationCount}</span> : null}
        </button>
        <button className="icon-pill" title="Quick actions">
          <Zap size={16} />
        </button>
        <button className="icon-pill" title="Settings">
          <Settings size={16} />
        </button>
        <button className="icon-pill" title="Log out" onClick={onLogout}>
          <LogOut size={16} />
        </button>
        <div className="profile">
          <div className="avatar">{initials}</div>
          <div>
            <div className="profile-name">{header.fullName}</div>
            <div className="profile-role">{header.role}</div>
          </div>
        </div>
      </div>
    </header>
  );
}
