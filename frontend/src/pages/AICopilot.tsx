import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Bot, Send, User } from 'lucide-react';
import { api } from '../api/client';
import type { Deployment } from '../api/types';

interface Turn {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  pending?: boolean;
}

const SUGGESTIONS = [
  'What is the riskiest thing in production right now?',
  'Which deployment should I be most worried about?',
  'Summarize our current architecture risks.',
  'What should the team fix first this week?',
];

export default function AICopilot() {
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState('');
  const [deployments, setDeployments] = useState<Deployment[]>([]);
  const [deploymentId, setDeploymentId] = useState<number | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextId = useRef(1);
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    let cancelled = false;
    // Deployment context is optional: the copilot answers fine without it, so a
    // failure here must not block the page.
    api
      .deployments()
      .then((list) => {
        if (!cancelled) setDeployments(list);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [turns]);

  const [searchParams, setSearchParams] = useSearchParams();
  const handedOver = useRef(false);

  const ask = useCallback(async function ask(question: string) {
    const trimmed = question.trim();
    if (!trimmed) return;

    const userTurn: Turn = { id: nextId.current++, role: 'user', text: trimmed };
    const pendingTurn: Turn = { id: nextId.current++, role: 'assistant', text: 'Thinking...', pending: true };
    setTurns((prev) => [...prev, userTurn, pendingTurn]);
    setInput('');
    setSending(true);
    setError(null);

    try {
      const response = await api.askCommand(trimmed, deploymentId);
      setTurns((prev) =>
        prev.map((turn) =>
          turn.id === pendingTurn.id ? { ...turn, text: response.answer, pending: false } : turn,
        ),
      );
    } catch (err) {
      // Drop the placeholder rather than leaving "Thinking..." stuck forever.
      setTurns((prev) => prev.filter((turn) => turn.id !== pendingTurn.id));
      setError(err instanceof Error ? err.message : 'Sentinel could not answer that right now.');
    } finally {
      setSending(false);
    }
  }, [deploymentId]);

  // Declared after `ask` on purpose: naming it in this effect's dependency array
  // before the const is initialised throws a temporal-dead-zone ReferenceError
  // during render, which silently broke the whole page.
  //
  // The top bar hands its search query over as ?q=. Ask it once on arrival, then
  // strip the param so a refresh or back-navigation does not re-ask it.
  useEffect(() => {
    const question = searchParams.get('q');
    if (!question || handedOver.current) return;
    handedOver.current = true;
    setSearchParams({}, { replace: true });
    void ask(question);
  }, [searchParams, setSearchParams, ask]);

  return (
    <div className="copilot-page">
      <div className="panel copilot-panel">
        <div className="copilot-head">
          <div className="chart-card-header copilot-heading">
            <Bot size={16} />
            <span>AI Copilot</span>
          </div>
          <select
            className="copilot-context"
            value={deploymentId ?? ''}
            onChange={(event) => setDeploymentId(event.target.value ? Number(event.target.value) : null)}
          >
            <option value="">No deployment context</option>
            {deployments.map((deployment) => (
              <option key={deployment.id} value={deployment.id}>
                {deployment.serviceName} #{deployment.id}
              </option>
            ))}
          </select>
        </div>

        <div className="copilot-thread">
          {turns.length === 0 ? (
            <div className="copilot-empty">
              <Bot size={28} />
              <p>Ask Sentinel about release risk, incidents, or architecture.</p>
              <div className="copilot-suggestions">
                {SUGGESTIONS.map((suggestion) => (
                  <button key={suggestion} className="copilot-suggestion" onClick={() => ask(suggestion)}>
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            turns.map((turn) => (
              <div key={turn.id} className={`copilot-turn copilot-turn-${turn.role}`}>
                <div className="copilot-avatar">
                  {turn.role === 'user' ? <User size={15} /> : <Bot size={15} />}
                </div>
                <div className={`copilot-bubble${turn.pending ? ' copilot-bubble-pending' : ''}`}>
                  {turn.text}
                </div>
              </div>
            ))
          )}
          <div ref={endRef} />
        </div>

        {error ? <div className="integrations-error">{error}</div> : null}

        <form
          className="copilot-composer"
          onSubmit={(event) => {
            event.preventDefault();
            ask(input);
          }}
        >
          <input
            type="text"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ask Sentinel a question..."
            aria-label="Ask Sentinel a question"
            disabled={sending}
          />
          <button type="submit" disabled={sending || !input.trim()} aria-label="Send">
            <Send size={16} />
          </button>
        </form>
      </div>
    </div>
  );
}
