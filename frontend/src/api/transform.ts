import type {
  ArchitectureBrain,
  AuditEvent,
  AuthResponse,
  Deployment,
  EngineeringDna,
  ExecutiveBriefing,
  Incident,
  OperatorConsole,
} from './types';
import type { ServiceEdge, ServiceNode, ServiceStatus } from '../types/dashboard';

const TIER_ORDER = ['tier-0', 'tier-1', 'tier-2', 'tier-3', 'tier-4', 'tier-5'];

function tierIndex(tier: string): number {
  const idx = TIER_ORDER.indexOf(tier);
  return idx === -1 ? TIER_ORDER.length : idx;
}

function dayKey(iso: string): string {
  return iso.slice(0, 10);
}

export function humanize(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

export function buildHeaderStats(auth: AuthResponse) {
  const hour = new Date().getHours();
  const timeGreeting = hour < 12 ? 'Good Morning' : hour < 18 ? 'Good Afternoon' : 'Good Evening';
  const localPart = auth.username.split('@')[0];
  const fullName = localPart
    .split(/[._-]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
  return {
    timeGreeting,
    firstName: fullName.split(' ')[0],
    fullName,
    role: humanize(auth.role),
    organizationName: auth.organizationName,
  };
}

export function buildMetricCards(deployments: Deployment[], incidents: Incident[], referenceDate: Date = new Date()) {
  const todayKey = dayKey(referenceDate.toISOString());

  const byDay = new Map<string, Deployment[]>();
  deployments.forEach((d) => {
    const key = dayKey(d.createdAt);
    byDay.set(key, [...(byDay.get(key) ?? []), d]);
  });

  const last10Days: string[] = [];
  for (let i = 9; i >= 0; i -= 1) {
    const d = new Date(referenceDate);
    d.setDate(d.getDate() - i);
    last10Days.push(dayKey(d.toISOString()));
  }

  const deploymentsSpark = last10Days.map((key) => (byDay.get(key) ?? []).length);
  const highRiskSpark = last10Days.map(
    (key) => (byDay.get(key) ?? []).filter((d) => d.riskAssessment && ['HIGH', 'CRITICAL'].includes(d.riskAssessment.level)).length,
  );

  const isToday = todayKey === dayKey(new Date().toISOString());
  const dayLabel = isToday ? 'Today' : 'Yesterday';
  const dayDeployments = byDay.get(todayKey) ?? [];
  const deploymentsToday = dayDeployments.length;
  const highRiskDeployments = dayDeployments.filter(
    (d) => d.riskAssessment && ['HIGH', 'CRITICAL'].includes(d.riskAssessment.level),
  ).length;

  const resolvedIncidents = incidents.filter((i) => i.status === 'RESOLVED' && i.resolvedAt);
  const mttrMinutes = resolvedIncidents.length
    ? Math.round(
        resolvedIncidents.reduce(
          (sum, i) => sum + (new Date(i.resolvedAt as string).getTime() - new Date(i.openedAt).getTime()) / 60000,
          0,
        ) / resolvedIncidents.length,
      )
    : null;

  return [
    {
      id: 'deployments',
      label: `Deployments ${dayLabel}`,
      value: String(deploymentsToday),
      caption: `${deployments.length} tracked total`,
      icon: 'box',
      accent: 'var(--blue)',
      spark: hasVariation(deploymentsSpark) ? deploymentsSpark : null,
    },
    {
      id: 'high-risk',
      label: 'High Risk Deployments',
      value: String(highRiskDeployments),
      caption: `of ${deploymentsToday} that day`,
      icon: 'alert-triangle',
      accent: 'var(--red)',
      spark: hasVariation(highRiskSpark) ? highRiskSpark : null,
    },
    {
      id: 'incidents',
      label: 'Active Incidents',
      value: String(incidents.length),
      caption: incidents.length ? `highest: ${worstSeverity(incidents)}` : 'none open',
      icon: 'alert-octagon',
      accent: 'var(--amber)',
      spark: null,
    },
    {
      id: 'mttr',
      label: 'MTTR',
      value: mttrMinutes !== null ? `${mttrMinutes}m` : '—',
      caption: mttrMinutes !== null ? `across ${resolvedIncidents.length} resolved` : 'no resolved incidents yet',
      icon: 'clock',
      accent: 'var(--cyan)',
      spark: null,
    },
  ];
}

function hasVariation(values: number[]): boolean {
  return new Set(values).size > 1;
}

function worstSeverity(incidents: Incident[]): string {
  const order = ['SEV1', 'SEV2', 'SEV3'];
  const worst = incidents.map((i) => i.severity).sort((a, b) => order.indexOf(a) - order.indexOf(b))[0];
  return worst;
}

export function buildServiceGraph(brain: ArchitectureBrain): { nodes: ServiceNode[]; edges: ServiceEdge[] } {
  const riskByService = new Map<string, ArchitectureSeverityLabel>();
  brain.risks.forEach((risk) => {
    const current = riskByService.get(risk.serviceName);
    if (!current || severityRank(risk.severity) > severityRank(current)) {
      riskByService.set(risk.serviceName, risk.severity);
    }
  });

  const tiers = new Map<number, ArchitectureBrain['services']>();
  brain.services.forEach((service) => {
    const idx = tierIndex(service.tier);
    tiers.set(idx, [...(tiers.get(idx) ?? []), service]);
  });

  // Dependencies can be circular (a real architecture risk Sentinel flags), so a
  // strict left-to-right DAG layout can't be derived. Instead place each tier on
  // its own concentric ring, spreading nodes evenly by angle within the ring.
  const tierKeys = [...tiers.keys()].sort((a, b) => a - b);
  const nodes: ServiceNode[] = [];
  const cx = 50;
  const cy = 50;

  tierKeys.forEach((tierKey, ringIndex) => {
    const servicesInTier = tiers.get(tierKey) ?? [];
    const radius = tierKeys.length > 1 ? 16 + (ringIndex / (tierKeys.length - 1)) * 22 : 28;
    servicesInTier.forEach((service, i) => {
      const angle = (i / servicesInTier.length) * 2 * Math.PI + ringIndex * 0.6;
      const severity = riskByService.get(service.serviceName);
      nodes.push({
        id: service.serviceName,
        name: service.serviceName,
        status: severityToStatus(severity),
        value: severity ? `${humanize(severity)} risk` : 'Healthy',
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
      });
    });
  });

  const nodeIds = new Set(nodes.map((n) => n.id));
  const edges: ServiceEdge[] = brain.dependencies
    .filter((dep) => nodeIds.has(dep.sourceService) && nodeIds.has(dep.targetService))
    .map((dep) => ({ from: dep.sourceService, to: dep.targetService }));

  return { nodes, edges };
}

type ArchitectureSeverityLabel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

function severityRank(severity: ArchitectureSeverityLabel): number {
  return ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].indexOf(severity);
}

function severityToStatus(severity: ArchitectureSeverityLabel | undefined): ServiceStatus {
  if (!severity) return 'healthy';
  if (severity === 'HIGH' || severity === 'CRITICAL') return 'high-risk';
  return 'warning';
}

export function buildRiskHeatmap(brain: ArchitectureBrain) {
  const counts: Record<ArchitectureSeverityLabel, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
  const riskyServices = new Set<string>();
  brain.risks.forEach((risk) => {
    counts[risk.severity] += 1;
    riskyServices.add(risk.serviceName);
  });
  const healthy = Math.max(brain.services.length - riskyServices.size, 0);

  return [
    { label: 'Critical', count: counts.CRITICAL, color: 'var(--red)' },
    { label: 'High', count: counts.HIGH, color: 'var(--amber)' },
    { label: 'Medium', count: counts.MEDIUM, color: 'var(--cyan)' },
    { label: 'Healthy', count: healthy, color: 'var(--green)' },
  ];
}

export interface RiskGlobeTeam {
  team: string;
  status: ServiceStatus;
  levelLabel: string;
  score: number;
  serviceCount: number;
  deploymentCount: number;
  evidence: string[];
}

export function buildRiskGlobe(brain: ArchitectureBrain, deployments: Deployment[]): RiskGlobeTeam[] {
  const teams = new Map<
    string,
    { rank: number; level: string; score: number; serviceCount: number; deploymentCount: number; evidence: string[] }
  >();

  function ensure(team: string) {
    let entry = teams.get(team);
    if (!entry) {
      entry = { rank: -1, level: '', score: 0, serviceCount: 0, deploymentCount: 0, evidence: [] };
      teams.set(team, entry);
    }
    return entry;
  }

  brain.services.forEach((service) => {
    ensure(service.ownerTeam).serviceCount += 1;
  });

  const riskByService = new Map<string, ArchitectureSeverityLabel>();
  brain.risks.forEach((risk) => {
    const current = riskByService.get(risk.serviceName);
    if (!current || severityRank(risk.severity) > severityRank(current)) {
      riskByService.set(risk.serviceName, risk.severity);
    }
  });

  brain.services.forEach((service) => {
    const severity = riskByService.get(service.serviceName);
    if (!severity) return;
    const entry = ensure(service.ownerTeam);
    const rank = severityRank(severity);
    if (rank > entry.rank) {
      entry.rank = rank;
      entry.level = severity;
    }
    const risk = brain.risks.find((r) => r.serviceName === service.serviceName && r.severity === severity);
    if (risk) entry.evidence.push(`${service.serviceName}: ${risk.explanation}`);
  });

  deployments.forEach((deployment) => {
    if (!deployment.riskAssessment) return;
    const entry = ensure(deployment.ownerTeam);
    entry.deploymentCount += 1;
    const rank = severityRank(deployment.riskAssessment.level);
    if (rank > entry.rank) {
      entry.rank = rank;
      entry.level = deployment.riskAssessment.level;
    }
    entry.score = Math.max(entry.score, deployment.riskAssessment.score);
    entry.evidence.push(`${deployment.serviceName}: ${deployment.riskAssessment.recommendation}`);
  });

  return [...teams.entries()].map(([team, entry]) => ({
    team,
    status: entry.rank <= 0 ? 'healthy' : entry.rank === 1 ? 'warning' : 'high-risk',
    levelLabel: entry.level || 'Healthy',
    score: entry.score,
    serviceCount: entry.serviceCount,
    deploymentCount: entry.deploymentCount,
    evidence: entry.evidence.slice(0, 4),
  }));
}

export function buildRiskTrend(deployments: Deployment[], referenceDate: Date = new Date()) {
  const byDay = new Map<string, number[]>();
  deployments.forEach((d) => {
    if (!d.riskAssessment) return;
    const key = dayKey(d.createdAt);
    byDay.set(key, [...(byDay.get(key) ?? []), d.riskAssessment.score]);
  });

  const days: { day: string; score: number | null }[] = [];
  for (let i = 6; i >= 0; i -= 1) {
    const date = new Date(referenceDate);
    date.setDate(date.getDate() - i);
    const key = dayKey(date.toISOString());
    const scores = byDay.get(key);
    days.push({
      day: date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      score: scores && scores.length ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null,
    });
  }
  return days;
}

export interface RiskProjectionPoint {
  day: string;
  historical: number | null;
  projected: number | null;
}

export interface RiskProjection {
  points: RiskProjectionPoint[];
  sampleSize: number;
  rSquared: number | null;
  trend: 'rising' | 'falling' | 'flat' | null;
}

export function buildRiskProjection(
  deployments: Deployment[],
  referenceDate: Date = new Date(),
  forecastDays = 3,
): RiskProjection {
  const history = buildRiskTrend(deployments, referenceDate);
  const knownPoints: { i: number; score: number }[] = [];
  history.forEach((h, i) => {
    if (h.score !== null) knownPoints.push({ i, score: h.score });
  });

  if (knownPoints.length < 2) {
    return {
      points: history.map((h) => ({ day: h.day, historical: h.score, projected: null })),
      sampleSize: knownPoints.length,
      rSquared: null,
      trend: null,
    };
  }

  const n = knownPoints.length;
  const sumX = knownPoints.reduce((s, p) => s + p.i, 0);
  const sumY = knownPoints.reduce((s, p) => s + p.score, 0);
  const sumXY = knownPoints.reduce((s, p) => s + p.i * p.score, 0);
  const sumXX = knownPoints.reduce((s, p) => s + p.i * p.i, 0);
  const denom = n * sumXX - sumX * sumX;
  const slope = denom === 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
  const intercept = (sumY - slope * sumX) / n;

  const meanY = sumY / n;
  const ssTot = knownPoints.reduce((s, p) => s + (p.score - meanY) ** 2, 0);
  const ssRes = knownPoints.reduce((s, p) => s + (p.score - (slope * p.i + intercept)) ** 2, 0);
  const rSquared = ssTot === 0 ? (ssRes === 0 ? 1 : 0) : Math.max(0, 1 - ssRes / ssTot);

  const lastIndex = history.length - 1;
  const points: RiskProjectionPoint[] = history.map((h) => ({ day: h.day, historical: h.score, projected: null }));
  points[lastIndex] = { ...points[lastIndex], projected: points[lastIndex].historical };

  for (let step = 1; step <= forecastDays; step += 1) {
    const dayIndex = lastIndex + step;
    const date = new Date(referenceDate);
    date.setDate(date.getDate() + step);
    const predictedScore = Math.round(Math.max(0, Math.min(100, slope * dayIndex + intercept)));
    points.push({
      day: date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      historical: null,
      projected: predictedScore,
    });
  }

  const trend = slope > 1 ? 'rising' : slope < -1 ? 'falling' : 'flat';

  return { points, sampleSize: n, rSquared, trend };
}

// `operator` is null for roles without access to the operator console (VIEWER),
// so the dashboard degrades to the panels that role can see rather than failing.
export function buildBusinessImpact(
  operator: OperatorConsole | null,
  deployments: Deployment[],
  incidents: Incident[],
) {
  const blockedToday = deployments.filter((d) => d.status === 'BLOCKED').length;
  const downtimeRisk = incidents.length ? worstSeverity(incidents).replace('SEV', 'Sev ') : 'Low';

  return {
    blockedToday,
    failedWebhooks: operator?.failedWebhookDeliveryCount ?? 0,
    attentionIntegrations: operator?.attentionIntegrationCount ?? 0,
    downtimeRisk,
  };
}

export function buildExecutiveBriefing(briefing: ExecutiveBriefing) {
  return briefing;
}

export function buildEngineeringHealth(dna: EngineeringDna) {
  const label = dna.overall >= 85 ? 'Excellent' : dna.overall >= 70 ? 'Good' : dna.overall >= 50 ? 'Fair' : 'Needs Attention';
  return { score: dna.overall, label, summary: dna.summary };
}

export function buildActivityFeed(events: AuditEvent[]) {
  return events.slice(0, 8).map((event) => {
    const { tone, icon } = classifyAction(event.action);
    return {
      id: String(event.id),
      time: new Date(event.createdAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' }),
      icon,
      tone,
      text: `${humanize(event.action)}: ${event.details}`,
    };
  });
}

function classifyAction(action: string): { tone: 'red' | 'blue' | 'amber' | 'green'; icon: string } {
  if (action.includes('BLOCK') || action.includes('FAIL')) return { tone: 'red', icon: 'alert-triangle' };
  if (action.includes('DEPLOY') || action.includes('WEBHOOK')) return { tone: 'blue', icon: 'rocket' };
  if (action.includes('INCIDENT') || action.includes('SIGNAL')) return { tone: 'amber', icon: 'file-warning' };
  return { tone: 'green', icon: 'undo' };
}
