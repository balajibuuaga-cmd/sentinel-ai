import { useNavigate } from 'react-router-dom';
import { Play, FileText, AlertOctagon, ShieldCheck, Hexagon } from 'lucide-react';

const actions = [
  { label: 'Run Simulation', icon: Play, to: '/simulator' },
  { label: 'Generate Report', icon: FileText, to: '/board-report' },
  { label: 'Create Incident', icon: AlertOctagon, to: '/incidents' },
  { label: 'Deploy Safely', icon: ShieldCheck, to: '/simulator' },
];

export default function ActionBar() {
  const navigate = useNavigate();

  return (
    <div className="action-bar">
      {actions.slice(0, 2).map((a) => (
        <button className="action-btn" key={a.label} onClick={() => navigate(a.to)}>
          <a.icon size={15} />
          {a.label}
        </button>
      ))}
      <div className="action-bar-center">
        <Hexagon size={20} />
      </div>
      {actions.slice(2).map((a) => (
        <button className="action-btn" key={a.label} onClick={() => navigate(a.to)}>
          <a.icon size={15} />
          {a.label}
        </button>
      ))}
    </div>
  );
}
