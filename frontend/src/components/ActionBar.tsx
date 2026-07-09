import { Play, FileText, AlertOctagon, ShieldCheck, Hexagon } from 'lucide-react';

const actions = [
  { label: 'Run Simulation', icon: Play },
  { label: 'Generate Report', icon: FileText },
  { label: 'Create Incident', icon: AlertOctagon },
  { label: 'Deploy Safely', icon: ShieldCheck },
];

export default function ActionBar() {
  return (
    <div className="action-bar">
      {actions.slice(0, 2).map((a) => (
        <button className="action-btn" key={a.label}>
          <a.icon size={15} />
          {a.label}
        </button>
      ))}
      <div className="action-bar-center">
        <Hexagon size={20} />
      </div>
      {actions.slice(2).map((a) => (
        <button className="action-btn" key={a.label}>
          <a.icon size={15} />
          {a.label}
        </button>
      ))}
    </div>
  );
}
