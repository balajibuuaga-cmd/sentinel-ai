import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, Bell, Zap, Settings, ShieldCheck, LogOut } from 'lucide-react';
import type { HeaderStatsData } from '../types/dashboard';

interface Props {
  header: HeaderStatsData;
  notificationCount: number;
  onLogout: () => void;
}

export default function TopBar({ header, notificationCount, onLogout }: Props) {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const searchRef = useRef<HTMLInputElement | null>(null);

  // The field advertises ⌘K, so the shortcut has to actually focus it.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        searchRef.current?.focus();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  function submitSearch(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    // Hand the question to the copilot page, which asks it on arrival.
    navigate(`/copilot?q=${encodeURIComponent(trimmed)}`);
    setQuery('');
  }

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

      <form className="topbar-search" onSubmit={submitSearch}>
        <Search size={16} />
        <input
          ref={searchRef}
          placeholder="Ask Sentinel anything..."
          aria-label="Ask Sentinel anything"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <kbd>⌘K</kbd>
      </form>

      <div className="topbar-actions">
        <button
          className="icon-pill"
          title="Secret Shield"
          onClick={() => navigate('/secret-shield')}
        >
          <ShieldCheck size={16} />
        </button>
        <button className="icon-pill" title="Notifications" onClick={() => navigate('/integrations')}>
          <Bell size={16} />
          {notificationCount > 0 ? <span className="dot-badge">{notificationCount}</span> : null}
        </button>
        <button
          className="icon-pill"
          title="Run a deployment analysis"
          onClick={() => navigate('/simulator')}
        >
          <Zap size={16} />
        </button>
        <button className="icon-pill" title="Settings" onClick={() => navigate('/settings')}>
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
