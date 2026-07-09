import { useEffect, useState } from 'react';
import { GitPullRequest, Check, Clock, Ban } from 'lucide-react';
import { api } from '../api/client';
import ConfidenceBadge from '../components/ConfidenceBadge';
import type { PullRequestDecision, PullRequestRecommendation, PullRequestReview } from '../api/types';

const recommendationTone: Record<PullRequestRecommendation, 'good' | 'warn' | 'bad'> = {
  MERGE: 'good',
  WAIT: 'warn',
  BLOCK: 'bad',
};

const emptyForm = {
  repository: 'sentinel-ai/payment-api',
  prNumber: 500,
  title: '',
  author: '',
  serviceName: 'payment-api',
  ownerTeam: 'Payments Platform',
  ciStatus: 'success',
  changedFiles: '',
};

export default function AIEngineer() {
  const [reviews, setReviews] = useState<PullRequestReview[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function loadReviews() {
    api
      .prReviews()
      .then((list) => {
        setReviews(list);
        if (list.length > 0 && selectedId === null) setSelectedId(list[0].id);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load PR reviews'));
  }

  useEffect(loadReviews, []);

  async function simulate() {
    if (!form.title.trim() || !form.author.trim() || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const created = await api.simulatePrReview({
        repository: form.repository,
        prNumber: Number(form.prNumber),
        title: form.title,
        author: form.author,
        serviceName: form.serviceName,
        ownerTeam: form.ownerTeam,
        ciStatus: form.ciStatus,
        changedFiles: form.changedFiles.split('\n').map((f) => f.trim()).filter(Boolean),
      });
      setReviews((prev) => [created, ...prev]);
      setSelectedId(created.id);
      setForm(emptyForm);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to simulate PR review');
    } finally {
      setSubmitting(false);
    }
  }

  async function decide(decision: PullRequestDecision) {
    if (selectedId === null) return;
    try {
      const updated = await api.decidePrReview(selectedId, {
        decision,
        actor: 'admin@sentinel.ai',
        note: `Decision recorded from AI Engineer console.`,
      });
      setReviews((prev) => prev.map((r) => (r.id === updated.id ? updated : r)));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to record decision');
    }
  }

  const selected = reviews.find((r) => r.id === selectedId) ?? null;

  return (
    <div className="engineer-page">
      <div className="panel engineer-form">
        <div className="engineer-form-header">
          <GitPullRequest size={16} /> Simulate a PR review
        </div>
        <label>
          Repository
          <input value={form.repository} onChange={(e) => setForm({ ...form, repository: e.target.value })} />
        </label>
        <div className="engineer-form-row">
          <label>
            PR number
            <input
              type="number"
              value={form.prNumber}
              onChange={(e) => setForm({ ...form, prNumber: Number(e.target.value) })}
            />
          </label>
          <label>
            CI status
            <select value={form.ciStatus} onChange={(e) => setForm({ ...form, ciStatus: e.target.value })}>
              <option value="success">success</option>
              <option value="failure">failure</option>
            </select>
          </label>
        </div>
        <label>
          Title
          <input
            placeholder="Refactor payment authorization retry path"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
        </label>
        <label>
          Author
          <input placeholder="david" value={form.author} onChange={(e) => setForm({ ...form, author: e.target.value })} />
        </label>
        <div className="engineer-form-row">
          <label>
            Service
            <input value={form.serviceName} onChange={(e) => setForm({ ...form, serviceName: e.target.value })} />
          </label>
          <label>
            Owner team
            <input value={form.ownerTeam} onChange={(e) => setForm({ ...form, ownerTeam: e.target.value })} />
          </label>
        </div>
        <label>
          Changed files (one per line)
          <textarea
            rows={3}
            placeholder="src/payments/AuthorizePayment.java"
            value={form.changedFiles}
            onChange={(e) => setForm({ ...form, changedFiles: e.target.value })}
          />
        </label>
        <button className="briefing-cta" onClick={simulate} disabled={submitting}>
          {submitting ? 'Reviewing...' : 'Ask: Should I merge this?'}
        </button>
        {error ? <div className="engineer-error">{error}</div> : null}
      </div>

      <div className="engineer-right-col">
      <div className="engineer-list">
        {reviews.map((r) => (
          <button
            key={r.id}
            className={`panel engineer-review-card${r.id === selectedId ? ' active' : ''}`}
            onClick={() => setSelectedId(r.id)}
          >
            <div className="engineer-review-top">
              <span className={`rec-badge tone-${recommendationTone[r.recommendation]}`}>{r.recommendation}</span>
              <span className="engineer-review-pr">{r.repository} #{r.prNumber}</span>
            </div>
            <div className="engineer-review-title">{r.title}</div>
            <div className="engineer-review-meta">Risk {r.riskScore}% &middot; {r.decision ?? 'No decision yet'}</div>
          </button>
        ))}
        {reviews.length === 0 ? <div className="page-empty-state">No PR reviews yet. Simulate one to get started.</div> : null}
      </div>

      {selected ? (
        <div className="panel engineer-detail">
          <div className="engineer-detail-header">
            <div>
              <span className={`rec-badge tone-${recommendationTone[selected.recommendation]}`}>
                {selected.recommendation}
              </span>
              <div className="engineer-detail-title">{selected.title}</div>
              <div className="engineer-detail-sub">
                {selected.repository} #{selected.prNumber} &middot; by {selected.author}
              </div>
            </div>
            <ConfidenceBadge confidence={Math.round(Math.abs(selected.riskScore - 50) * 2)} />
          </div>
          <p className="engineer-detail-explanation">{selected.explanation}</p>
          <div className="engineer-decision-row">
            <button className="action-btn tone-good" onClick={() => decide('MERGED')}>
              <Check size={14} /> Merge
            </button>
            <button className="action-btn tone-warn" onClick={() => decide('WAITING')}>
              <Clock size={14} /> Wait
            </button>
            <button className="action-btn tone-bad" onClick={() => decide('BLOCKED')}>
              <Ban size={14} /> Block
            </button>
          </div>
          {selected.decisionNote ? <div className="engineer-decision-note">{selected.decisionNote}</div> : null}
        </div>
      ) : null}
      </div>
    </div>
  );
}
