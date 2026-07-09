import { Gauge } from 'lucide-react';

interface Props {
  confidence: number;
  evidence?: string[];
}

export default function ConfidenceBadge({ confidence, evidence = [] }: Props) {
  const tone = confidence >= 80 ? 'good' : confidence >= 50 ? 'warn' : 'bad';

  return (
    <div className={`confidence-badge tone-${tone}`}>
      <div className="confidence-badge-head">
        <Gauge size={14} />
        <span>Confidence</span>
        <span className="confidence-badge-value">{confidence}%</span>
      </div>
      {evidence.length > 0 ? (
        <ul className="confidence-badge-evidence">
          {evidence.map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
