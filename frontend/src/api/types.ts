export interface AuthResponse {
  token: string;
  username: string;
  role: string;
  tenantId: string;
  organizationName: string;
}

export interface RiskReason {
  category: string;
  evidence: string;
  impact: number;
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface RiskAssessment {
  score: number;
  level: RiskLevel;
  recommendation: string;
  aiExplanation: string;
  reasons: RiskReason[];
  assessedAt: string;
}

export type DeploymentStatus = 'READY_FOR_REVIEW' | 'APPROVED' | 'BLOCKED' | 'DEPLOYED' | 'ROLLED_BACK';

export interface Deployment {
  id: number;
  tenantId: string;
  organizationName: string;
  deploymentKey: string;
  serviceName: string;
  ownerTeam: string;
  environment: string;
  commitSha: string;
  pullRequestTitle: string;
  dependencies: string[];
  createdAt: string;
  status: DeploymentStatus;
  riskAssessment: RiskAssessment | null;
}

export type IncidentSeverity = 'SEV1' | 'SEV2' | 'SEV3';
export type IncidentStatus = 'ACTIVE' | 'INVESTIGATING' | 'MITIGATING' | 'RESOLVED';

export interface IncidentTimelineEvent {
  occurredAt: string;
  actor: string;
  label: string;
  detail: string;
}

export interface Incident {
  id: number;
  incidentKey: string;
  deploymentId: number;
  deploymentKey: string;
  serviceName: string;
  ownerTeam: string;
  environment: string;
  severity: IncidentSeverity;
  status: IncidentStatus;
  riskScore: number;
  summary: string;
  affectedSystems: string;
  commanderBrief: string;
  recommendedAction: string;
  openedAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  timeline: IncidentTimelineEvent[];
}

export interface ArchitectureServiceNode {
  id: number;
  serviceName: string;
  ownerTeam: string;
  runtime: string;
  tier: string;
  repository: string;
  description: string;
}

export type ArchitectureDependencyType = 'API' | 'DATABASE' | 'QUEUE' | 'CACHE' | 'AUTH' | 'EXTERNAL';

export interface ArchitectureDependency {
  id: number;
  sourceService: string;
  targetService: string;
  dependencyType: ArchitectureDependencyType;
  criticality: string;
  notes: string;
}

export type ArchitectureSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ArchitectureRiskType =
  | 'SINGLE_POINT_OF_FAILURE'
  | 'CIRCULAR_DEPENDENCY'
  | 'SHARED_DATABASE'
  | 'MISSING_OWNER'
  | 'HIGH_BLAST_RADIUS';

export interface ArchitectureRisk {
  id: number;
  serviceName: string;
  riskType: ArchitectureRiskType;
  severity: ArchitectureSeverity;
  explanation: string;
  recommendation: string;
}

export interface ArchitectureBrain {
  summary: string;
  recommendedRefactor: string;
  serviceCount: number;
  dependencyCount: number;
  riskCount: number;
  services: ArchitectureServiceNode[];
  dependencies: ArchitectureDependency[];
  risks: ArchitectureRisk[];
}

export interface MetricInsight {
  label: string;
  value: string;
}

export interface ExecutiveBriefing {
  greeting: string;
  summary: string;
  recommendationTitle: string;
  recommendation: string;
  chiefBriefing: string;
  metrics: MetricInsight[];
}

export interface DnaScore {
  label: string;
  value: number;
}

export interface EngineeringDna {
  overall: number;
  summary: string;
  scores: DnaScore[];
}

export interface OperatorFailure {
  provider: string;
  category: string;
  detail: string;
  requestId: string;
  createdAt: string;
}

export interface BackgroundJobSummary {
  [key: string]: unknown;
}

export interface OperatorConsole {
  requestId: string;
  tenantId: string;
  organizationName: string;
  runtimeMode: string;
  deploymentCount: number;
  connectedIntegrationCount: number;
  attentionIntegrationCount: number;
  failedWebhookDeliveryCount: number;
  readinessStatus: string;
  metrics: Record<string, number>;
  recentFailures: OperatorFailure[];
}

export interface AuditEvent {
  id: number;
  actor: string;
  action: string;
  target: string;
  details: string;
  createdAt: string;
}

export interface CommandResponse {
  answer: string;
}

export interface MemoryEvent {
  date: string;
  title: string;
  detail: string;
}

export interface DeploymentMemory {
  deploymentId: number;
  serviceName: string;
  confidence: number;
  summary: string;
  events: MemoryEvent[];
}

export type PullRequestRecommendation = 'MERGE' | 'WAIT' | 'BLOCK';
export type PullRequestDecision = 'MERGED' | 'WAITING' | 'BLOCKED';

export interface PullRequestReview {
  id: number;
  repository: string;
  prNumber: number;
  title: string;
  author: string;
  serviceName: string;
  ownerTeam: string;
  ciStatus: string;
  changedFiles: string[];
  linkedDeploymentId: number | null;
  riskScore: number;
  recommendation: PullRequestRecommendation;
  explanation: string;
  decision: PullRequestDecision | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface PullRequestReviewRequest {
  repository: string;
  prNumber: number;
  title: string;
  author: string;
  serviceName: string;
  ownerTeam: string;
  ciStatus: string;
  changedFiles: string[];
}

export interface PullRequestDecisionRequest {
  decision: PullRequestDecision;
  actor: string;
  note: string;
}

export interface IncidentStatusUpdateRequest {
  status: IncidentStatus;
  actor: string;
  note: string;
}

export type IntegrationProvider = 'GITHUB' | 'JIRA' | 'CI';
export type IntegrationStatus = 'AVAILABLE' | 'CONNECTED' | 'NEEDS_ATTENTION' | 'DISCONNECTED';
export type IntegrationSyncStatus = 'SUCCESS' | 'DEGRADED' | 'FAILED';

export interface IntegrationConnection {
  id: number;
  tenantId: string;
  organizationName: string;
  provider: IntegrationProvider;
  status: IntegrationStatus;
  displayName: string;
  installUrl: string;
  scopes: string;
  tokenSecretRef: string;
  externalAccount: string | null;
  connectedAt: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: IntegrationSyncStatus;
  healthScore: number;
  statusDetail: string;
}

export interface IntegrationSyncEvent {
  id: number;
  tenantId: string;
  organizationName: string;
  integrationConnectionId: number;
  provider: IntegrationProvider;
  status: IntegrationSyncStatus;
  recordsInspected: number;
  latencyMs: number;
  healthScore: number;
  detail: string;
  createdAt: string;
}

export interface IntegrationInstallRequest {
  externalAccount?: string | null;
  code?: string | null;
  state?: string | null;
}

export type BackgroundJobStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';
export type BackgroundJobType = 'PROVIDER_SYNC_RETRY' | 'INCIDENT_FOLLOW_UP' | 'WEBHOOK_REPLAY';

export interface BackgroundJob {
  id: number;
  tenantId: string;
  organizationName: string;
  jobType: BackgroundJobType;
  status: BackgroundJobStatus;
  targetType: string;
  targetId: number;
  targetLabel: string;
  payload: string;
  attempts: number;
  maxAttempts: number;
  nextRunAt: string;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export type WebhookDeliveryStatus = 'RECEIVED' | 'SUCCEEDED' | 'FAILED' | 'REPLAY_QUEUED' | 'REPLAYED' | 'EXPIRED';

export interface WebhookDelivery {
  id: number;
  tenantId: string;
  organizationName: string;
  provider: string;
  externalDeliveryId: string;
  eventType: string;
  status: WebhookDeliveryStatus;
  payload: string;
  requestId: string;
  failureReason: string | null;
  targetReference: string | null;
  replayAttempts: number;
  nextReplayAt: string | null;
  maxReplayAttempts: number;
  expiresAt: string | null;
  replayEligibility: string;
  createdAt: string;
  updatedAt: string;
  processedAt: string | null;
  lastReplayedAt: string | null;
}

export interface OnboardingStep {
  label: string;
  complete: boolean;
  detail: string;
}

export interface OrganizationProfile {
  tenantId: string;
  organizationName: string;
  workspaceStatus: string;
  deploymentCount: number;
  prReviewCount: number;
  architectureServiceCount: number;
  auditEventCount: number;
  connectedIntegrationCount: number;
  onboardingSteps: OnboardingStep[];
}

export interface TeamMember {
  id: number;
  email: string;
  role: string;
  locked: boolean;
  you: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface TeamInviteRequest {
  email: string;
  role: string;
}

export interface AccountProfile {
  email: string;
  role: string;
  tenantId: string;
  organizationName: string;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface EngineeringPlaybook {
  id: string;
  title: string;
  category: string;
  summary: string;
  checks: string[];
  sentinelActions: string[];
}

export interface BackendReadinessCheck {
  category: string;
  status: string;
  score: number;
  evidence: string;
  gap: string;
  nextAction: string;
}

export interface BackendReadinessAssessment {
  overallScore: number;
  maturityLevel: string;
  summary: string;
  checks: BackendReadinessCheck[];
  nextActions: string[];
}

export interface CiSignalRequest {
  provider: string;
  repository: string;
  serviceName: string;
  ownerTeam: string;
  environment: string;
  commitSha: string;
  pipelineName: string;
  buildUrl?: string | null;
  status: string;
  failedTests?: number | null;
  coverageDelta?: number | null;
  actor?: string | null;
  failedSuites?: string[];
  dependencies?: string[];
}
