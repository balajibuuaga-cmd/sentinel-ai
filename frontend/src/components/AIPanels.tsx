import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles, CheckCircle2, ChevronDown, Maximize2, SendHorizontal } from 'lucide-react';
import { api } from '../api/client';
import HolographicAvatar from './HolographicAvatar';
import type { ExecutiveBriefing as ExecutiveBriefingData } from '../api/types';

const SUGGESTIONS = [
  'Should we deploy payment-api?',
  'Show me top risks today',
  'What is our engineering DNA score?',
];

export function ExecutiveBriefing({ briefing }: { briefing: ExecutiveBriefingData }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="panel briefing-panel">
      <div className="briefing-avatar">
        <div className="briefing-avatar-ring" />
        <Sparkles size={26} />
      </div>
      <div className="briefing-title-row">
        <div>
          <div className="briefing-title">AI Executive Briefing</div>
          <div className="briefing-sub">{briefing.greeting}</div>
        </div>
      </div>
      <p className="briefing-lead">{briefing.summary}</p>
      <ul className="briefing-list">
        {briefing.metrics.map((metric) => (
          <li key={metric.label}>
            <CheckCircle2 size={15} className="tone-good" />
            <span>
              <b>{metric.label}:</b> {metric.value}
            </span>
          </li>
        ))}
      </ul>
      <button className="briefing-cta" onClick={() => setExpanded((v) => !v)}>
        {expanded ? 'Hide Full AI Briefing' : 'View Full AI Briefing'}
        <ChevronDown size={15} style={{ transform: expanded ? 'rotate(180deg)' : 'none' }} />
      </button>
      {expanded ? (
        <div className="briefing-detail">
          <div className="briefing-detail-title">{briefing.recommendationTitle}</div>
          <p>{briefing.recommendation}</p>
          <p className="briefing-detail-chief">{briefing.chiefBriefing}</p>
        </div>
      ) : null}
    </div>
  );
}

export function AICopilot() {
  const [value, setValue] = useState('');
  const [pending, setPending] = useState(false);
  const [conversation, setConversation] = useState<{ role: 'user' | 'ai'; text: string }[]>([]);

  async function send(command: string) {
    if (!command.trim() || pending) return;
    setConversation((c) => [...c, { role: 'user', text: command }]);
    setValue('');
    setPending(true);
    try {
      const response = await api.askCommand(command);
      setConversation((c) => [...c, { role: 'ai', text: response.answer }]);
    } catch {
      setConversation((c) => [...c, { role: 'ai', text: 'Sentinel AI could not answer that right now.' }]);
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="panel copilot-panel">
      <div className="copilot-header">
        <div className="copilot-title">
          <Sparkles size={15} />
          <span>AI Copilot</span>
        </div>
        <div className="copilot-header-right">
          <span className="beta-badge">BETA</span>
          {/* This panel is the dashboard-sized copilot; the full conversation
              view lives on its own page. */}
          <Link className="copilot-close" to="/copilot" title="Open full AI Copilot">
            <Maximize2 size={14} />
          </Link>
        </div>
      </div>

      <div className="copilot-avatar-wrap">
        <HolographicAvatar active={pending} size={140} />
      </div>

      {conversation.length === 0 ? (
        <p className="copilot-hint">Ask Sentinel AI anything about your engineering org...</p>
      ) : (
        <div className="copilot-conversation">
          {conversation.map((turn, i) => (
            <div key={i} className={`copilot-turn copilot-turn-${turn.role}`}>
              {turn.text}
            </div>
          ))}
          {pending ? <div className="copilot-turn copilot-turn-ai">Thinking...</div> : null}
        </div>
      )}

      <div className="copilot-suggestions">
        {SUGGESTIONS.map((s) => (
          <button key={s} className="copilot-chip" onClick={() => send(s)}>
            {s}
          </button>
        ))}
      </div>
      <div className="copilot-input">
        <input
          placeholder="Type your question..."
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && send(value)}
        />
        <button className="copilot-send" onClick={() => send(value)} disabled={pending}>
          <SendHorizontal size={15} />
        </button>
      </div>
    </div>
  );
}
