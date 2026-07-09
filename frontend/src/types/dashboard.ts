export type ServiceStatus = 'healthy' | 'warning' | 'high-risk' | 'unknown';

export interface ServiceNode {
  id: string;
  name: string;
  status: ServiceStatus;
  value: string;
  x: number;
  y: number;
}

export interface ServiceEdge {
  from: string;
  to: string;
}

export interface MetricCardData {
  id: string;
  label: string;
  value: string;
  caption: string;
  icon: string;
  accent: string;
  spark: number[] | null;
}

export interface ActivityItemData {
  id: string;
  time: string;
  icon: string;
  tone: 'red' | 'blue' | 'amber' | 'green';
  text: string;
}

export interface RiskHeatmapSlice {
  label: string;
  count: number;
  color: string;
}

export interface RiskTrendPoint {
  day: string;
  score: number | null;
}

export interface BusinessImpactData {
  blockedToday: number;
  failedWebhooks: number;
  attentionIntegrations: number;
  downtimeRisk: string;
}

export interface EngineeringHealthData {
  score: number;
  label: string;
  summary: string;
}

export interface HeaderStatsData {
  timeGreeting: string;
  firstName: string;
  fullName: string;
  role: string;
  organizationName: string;
}
