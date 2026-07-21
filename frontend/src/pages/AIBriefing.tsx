import { useEffect, useState } from 'react';
import { Sparkles, ArrowRight } from 'lucide-react';
import { api } from '../api/client';
import { buildBriefingTimeline, groupBriefingEventsByDay, localGreeting } from '../api/transform';
import type { BriefingEvent, BriefingEventKind } from '../api/transform';
import type { Deployment, ExecutiveBriefing, Incident, PullRequestReview } from '../api/types';

interface Snapshot {
  briefing: ExecutiveBriefing;
  deploymentsToday: number;
  activeIncidents: number;
  prsReviewed: number;
  rollbacks: number;
  productionOutages: number;
}

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function buildSnapshot(briefing: ExecutiveBriefing, deployments: Deployment[], incidents: Incident[], reviews: PullRequestReview[]): Snapshot {
  const today = todayKey();
  return {
    briefing,
    deploymentsToday: deployments.filter((d) => d.createdAt.slice(0, 10) === today).length,
    activeIncidents: incidents.length,
    prsReviewed: reviews.length,
    rollbacks: deployments.filter((d) => d.status === 'ROLLED_BACK').length,
    productionOutages: incidents.filter((i) => i.severity === 'SEV1').length,
  };
}

// Enough to read without scrolling the page away from the summary.
const EVENTS_PER_PAGE = 8;

const KIND_LABEL: Record<BriefingEventKind, string> = {
  DEPLOYMENT: 'Deployment',
  INCIDENT_OPENED: 'Incident',
  INCIDENT_RESOLVED: 'Resolved',
  PR_REVIEW: 'PR review',
  PR_DECISION: 'PR decision',
  AUDIT: 'Activity',
};

function formatDay(day: string): string {
  const date = new Date(`${day}T00:00:00`);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (day === today.toISOString().slice(0, 10)) return 'Today';
  if (day === yesterday.toISOString().slice(0, 10)) return 'Yesterday';
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' });
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export default function AIBriefing() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [timeline, setTimeline] = useState<BriefingEvent[]>([]);
  const [kindFilter, setKindFilter] = useState<BriefingEventKind | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.executiveBriefing(),
      api.deployments(),
      api.incidents(),
      api.prReviews(),
      api.auditEvents(),
    ])
      .then(([briefing, deployments, incidents, reviews, auditEvents]) => {
        if (cancelled) return;
        setSnapshot(buildSnapshot(briefing, deployments, incidents, reviews));
        setTimeline(buildBriefingTimeline(deployments, incidents, reviews, auditEvents));
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : 'Failed to load briefing'));
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) {
    return <div className="page-empty-state">Could not load the AI Briefing: {error}</div>;
  }

  if (!snapshot) {
    return <div className="page-empty-state">Preparing this morning's briefing...</div>;
  }

  const savings = snapshot.briefing.metrics.find((m) => m.label === 'Expected savings')?.value ?? '$0';
  const filtered = kindFilter === 'ALL'
    ? timeline
    : timeline.filter((event) =>
        kindFilter === 'INCIDENT_OPENED'
          ? event.kind === 'INCIDENT_OPENED' || event.kind === 'INCIDENT_RESOLVED'
          : kindFilter === 'PR_REVIEW'
          ? event.kind === 'PR_REVIEW' || event.kind === 'PR_DECISION'
          : event.kind === kindFilter,
      );
  const pageCount = Math.max(1, Math.ceil(filtered.length / EVENTS_PER_PAGE));
  // Clamp rather than store a corrected page: filtering to a smaller set can
  // leave the current page past the end.
  const currentPage = Math.min(page, pageCount - 1);
  const start = currentPage * EVENTS_PER_PAGE;
  const pageEvents = filtered.slice(start, start + EVENTS_PER_PAGE);
  const visibleDays = groupBriefingEventsByDay(pageEvents);

  return (
    <div className="ai-briefing-page">
      <div className="panel ai-briefing-hero">
        <div className="ai-briefing-icon">
          <Sparkles size={28} />
        </div>
        <h1>{localGreeting()}.</h1>
        <p className="ai-briefing-sub">Here is what happened across your engineering organization.</p>

        <div className="ai-briefing-stats">
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.deploymentsToday}</span>
            <span className="ai-briefing-stat-label">Deployments</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.activeIncidents}</span>
            <span className="ai-briefing-stat-label">Incidents</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.prsReviewed}</span>
            <span className="ai-briefing-stat-label">PRs Reviewed</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.rollbacks}</span>
            <span className="ai-briefing-stat-label">Rollbacks</span>
          </div>
          <div className="ai-briefing-stat">
            <span className="ai-briefing-stat-value">{snapshot.productionOutages}</span>
            <span className="ai-briefing-stat-label">Production Outages</span>
          </div>
        </div>

        <div className="ai-briefing-divider" />

        <div className="ai-briefing-block">
          <div className="ai-briefing-block-label">AI Summary</div>
          <p>{snapshot.briefing.summary}</p>
        </div>

        <div className="ai-briefing-block">
          <div className="ai-briefing-block-label">Recommendation</div>
          <p className="ai-briefing-recommendation">
            <ArrowRight size={16} /> {snapshot.briefing.recommendationTitle}
          </p>
          <p>{snapshot.briefing.recommendation}</p>
        </div>

        <div className="ai-briefing-savings">
          <span>Expected savings</span>
          <strong>{savings}</strong>
        </div>
      </div>

      {/* The summary above answers "how did we do". This answers "what actually
          happened", event by event, which is what a briefing is for. */}
      <div className="panel briefing-log">
        <div className="briefing-log-head">
          <div className="chart-card-header">Everything that happened</div>
          <div className="briefing-filter-row">
            {(['ALL', 'DEPLOYMENT', 'INCIDENT_OPENED', 'PR_REVIEW', 'AUDIT'] as const).map((kind) => (
              <button
                key={kind}
                className={`briefing-filter${kindFilter === kind ? ' active' : ''}`}
                onClick={() => {
                  setKindFilter(kind);
                  setPage(0);
                }}
              >
                {kind === 'ALL' ? `All ${timeline.length}` : KIND_LABEL[kind]}
              </button>
            ))}
          </div>
        </div>

        {visibleDays.length === 0 ? (
          <div className="page-empty-state">Nothing has been recorded yet.</div>
        ) : (
          visibleDays.map((group) => (
            <section key={group.day} className="briefing-day">
              <h3 className="briefing-day-heading">{formatDay(group.day)}</h3>
              <ol className="briefing-events">
                {group.events.map((event) => (
                  <li key={event.key} className={`briefing-event sev-${event.severity}`}>
                    <div className="briefing-event-time">{formatTime(event.at)}</div>
                    <div className="briefing-event-body">
                      <div className="briefing-event-head">
                        <span className={`briefing-kind kind-${event.kind.toLowerCase()}`}>
                          {KIND_LABEL[event.kind]}
                        </span>
                        <span className="briefing-event-title">{event.title}</span>
                      </div>
                      {event.subtitle ? <p className="briefing-event-subtitle">{event.subtitle}</p> : null}
                      {event.detail ? <p className="briefing-event-detail">{event.detail}</p> : null}
                      {event.facts.length > 0 ? (
                        <div className="briefing-facts">
                          {event.facts.map((fact) => (
                            <span key={`${event.key}-${fact.label}`} className="briefing-fact">
                              <span className="briefing-fact-label">{fact.label}</span>
                              {fact.value}
                            </span>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          ))
        )}

        {filtered.length > EVENTS_PER_PAGE ? (
          <div className="briefing-pager">
            <button
              className="briefing-pager-btn"
              onClick={() => setPage(currentPage - 1)}
              disabled={currentPage === 0}
            >
              Previous
            </button>
            <span className="briefing-pager-status">
              {start + 1}&ndash;{Math.min(start + EVENTS_PER_PAGE, filtered.length)} of {filtered.length}
            </span>
            <button
              className="briefing-pager-btn"
              onClick={() => setPage(currentPage + 1)}
              disabled={currentPage >= pageCount - 1}
            >
              Next
            </button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
