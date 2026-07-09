import { useEffect, useState } from 'react';
import { Brain, Send, Clock } from 'lucide-react';
import { api } from '../api/client';
import ConfidenceBadge from '../components/ConfidenceBadge';
import type { Deployment, DeploymentMemory } from '../api/types';

export default function AIMemory() {
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [memory, setMemory] = useState<DeploymentMemory | null>(null);
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<string | null>(null);
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .deployments()
      .then((list) => {
        setDeployments(list);
        if (list.length > 0) setSelectedId(list[0].id);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load deployments'));
  }, []);

  useEffect(() => {
    if (selectedId === null) return;
    api
      .deploymentMemory(selectedId)
      .then(setMemory)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load memory'));
  }, [selectedId]);

  async function ask() {
    if (!question.trim() || asking) return;
    setAsking(true);
    setAnswer(null);
    try {
      const response = await api.askCommand(question, selectedId);
      setAnswer(response.answer);
    } catch {
      setAnswer('Sentinel AI could not answer that right now.');
    } finally {
      setAsking(false);
    }
  }

  if (error) {
    return <div className="page-empty-state">Could not load AI Memory: {error}</div>;
  }

  return (
    <div className="memory-page">
      <div className="panel memory-list">
        <div className="memory-list-header">Deployments</div>
        <div className="memory-list-items">
          {deployments.map((d) => (
            <button
              key={d.id}
              className={`memory-list-item${d.id === selectedId ? ' active' : ''}`}
              onClick={() => setSelectedId(d.id)}
            >
              <span className="memory-list-item-service">{d.serviceName}</span>
              <span className="memory-list-item-key">{d.deploymentKey}</span>
            </button>
          ))}
          {deployments.length === 0 ? <div className="service-detail-empty-line">No deployments yet.</div> : null}
        </div>
      </div>

      <div className="memory-detail">
        <div className="panel memory-ask">
          <div className="memory-ask-header">
            <Brain size={16} /> Ask why something happened
          </div>
          <div className="memory-ask-input">
            <input
              placeholder={memory ? `Why did ${memory.serviceName} fail?` : 'Ask a question...'}
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && ask()}
            />
            <button className="copilot-send" onClick={ask} disabled={asking}>
              <Send size={15} />
            </button>
          </div>
          {answer ? <p className="memory-answer">{answer}</p> : null}
        </div>

        {memory ? (
          <div className="panel memory-timeline-panel">
            <div className="memory-timeline-header">
              <div>
                <div className="memory-timeline-service">{memory.serviceName}</div>
                <p className="memory-timeline-summary">{memory.summary}</p>
              </div>
              <ConfidenceBadge confidence={memory.confidence} />
            </div>

            <ul className="memory-timeline">
              {memory.events.map((event, i) => (
                <li key={i}>
                  <div className="memory-timeline-dot" />
                  <div className="memory-timeline-content">
                    <div className="memory-timeline-date">
                      <Clock size={12} /> {event.date}
                    </div>
                    <div className="memory-timeline-title">{event.title}</div>
                    <div className="memory-timeline-detail">{event.detail}</div>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        ) : (
          <div className="page-empty-state">Select a deployment to see its memory chain.</div>
        )}
      </div>
    </div>
  );
}
