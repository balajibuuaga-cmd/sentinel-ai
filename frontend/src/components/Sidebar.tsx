import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Sparkles,
  ShieldAlert,
  Rocket,
  AlertOctagon,
  Boxes,
  Bot,
  BarChart3,
  BookOpen,
  Plug,
  Users,
  Settings,
  Hexagon,
  Plus,
  Brain,
  GitPullRequest,
  Dna,
  FileText,
  Server,
  ScanSearch,
  Crown,
  Globe2,
} from 'lucide-react';
import type { EngineeringHealthData } from '../types/dashboard';

const RADIUS = 42;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

interface Props {
  health: EngineeringHealthData;
  incidentCount: number;
  riskCount: number;
}

export default function Sidebar({ health, incidentCount, riskCount }: Props) {
  const offset = CIRCUMFERENCE - (health.score / 100) * CIRCUMFERENCE;

  const navItems = [
    { label: 'Command Center', icon: LayoutDashboard, to: '/', end: true },
    { label: 'AI Briefing', icon: Sparkles, to: '/briefing' },
    { label: 'AI Memory', icon: Brain, to: '/memory' },
    { label: 'Risks & Recommendations', icon: ShieldAlert, badge: riskCount },
    { label: 'Deployments', icon: Rocket, to: '/simulator' },
    { label: 'AI Engineer', icon: GitPullRequest, to: '/pr-review' },
    { label: 'Incidents', icon: AlertOctagon, badge: incidentCount, to: '/incidents' },
    { label: 'AI Investigation', icon: ScanSearch, to: '/investigation' },
    { label: 'Risk Globe', icon: Globe2, to: '/risk-globe' },
    { label: 'Architecture 3D', icon: Boxes, to: '/architecture' },
    { label: 'Engineering DNA', icon: Dna, to: '/engineering-dna' },
    { label: 'Board Report', icon: FileText, to: '/board-report' },
    { label: 'Executive Mode', icon: Crown, to: '/executive' },
    { label: 'AI Copilot', icon: Bot },
    { label: 'Analytics', icon: BarChart3 },
    { label: 'Knowledge Base', icon: BookOpen },
    { label: 'Integrations', icon: Plug, to: '/integrations' },
    { label: 'Operator Console', icon: Server, to: '/operator' },
    { label: 'Teams', icon: Users },
    { label: 'Settings', icon: Settings },
  ];

  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark">
          <Hexagon size={22} strokeWidth={2.2} />
        </div>
        <div>
          <div className="brand-title">Sentinel AI</div>
          <div className="brand-sub">Engineering Decision Engine</div>
        </div>
      </div>

      <nav className="nav">
        {navItems.map((item) =>
          item.to ? (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              <item.icon size={17} strokeWidth={2} />
              <span>{item.label}</span>
              {item.badge ? <span className="nav-badge">{item.badge}</span> : null}
            </NavLink>
          ) : (
            <button key={item.label} className="nav-item nav-item-disabled" disabled>
              <item.icon size={17} strokeWidth={2} />
              <span>{item.label}</span>
              {item.badge ? <span className="nav-badge">{item.badge}</span> : null}
            </button>
          ),
        )}
      </nav>

      <div className="health-card" title={health.summary}>
        <div className="health-ring-wrap">
          <svg viewBox="0 0 100 100" className="health-ring">
            <circle cx="50" cy="50" r={RADIUS} className="health-ring-track" />
            <circle
              cx="50"
              cy="50"
              r={RADIUS}
              className="health-ring-fill"
              strokeDasharray={CIRCUMFERENCE}
              strokeDashoffset={offset}
            />
          </svg>
          <div className="health-ring-value">
            <span className="health-score">{health.score}</span>
            <span className="health-total">/100</span>
          </div>
        </div>
        <div className="health-label">{health.label}</div>
        <div className="health-delta">Engineering DNA score</div>
        <button className="create-analysis-btn">
          <Plus size={15} />
          Create New Analysis
        </button>
      </div>
    </aside>
  );
}
