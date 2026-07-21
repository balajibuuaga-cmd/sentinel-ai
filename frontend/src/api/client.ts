import type {
  AccountProfile,
  AiUsageSummary,
  ArchitectureBrain,
  AuditEvent,
  AuthModeStatus,
  AuthResponse,
  BackendReadinessAssessment,
  BackgroundJob,
  CiSignalRequest,
  CommandResponse,
  Deployment,
  DeploymentMemory,
  EngineeringDna,
  EngineeringPlaybook,
  ErrorEventView,
  ExecutiveBriefing,
  Incident,
  IncidentRemediationStep,
  IncidentStatusUpdateRequest,
  SecretScanResponse,
  IntegrationConnection,
  IntegrationInstallRequest,
  IntegrationProvider,
  IntegrationSyncEvent,
  LoginResult,
  MfaEnrollResponse,
  OperatorConsole,
  OrganizationProfile,
  PullRequestDecisionRequest,
  PullRequestReview,
  PullRequestReviewRequest,
  TeamInviteRequest,
  TeamMember,
  WebhookDelivery,
} from './types';

const TOKEN_STORAGE_KEY = 'sentinel-ai-token';

let token: string | null = localStorage.getItem(TOKEN_STORAGE_KEY);

function setToken(value: string) {
  token = value;
  localStorage.setItem(TOKEN_STORAGE_KEY, value);
}

export function hasStoredSession(): boolean {
  return token !== null;
}

export function clearSession() {
  token = null;
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

function decodeJwtPayload(jwt: string): Record<string, unknown> | null {
  try {
    const segment = jwt.split('.')[1];
    if (!segment) return null;
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

export function currentSession(): AuthResponse | null {
  if (!token) return null;
  const claims = decodeJwtPayload(token);
  if (!claims) return null;
  return {
    token,
    username: String(claims.sub ?? ''),
    role: String(claims.role ?? ''),
    tenantId: String(claims.tenantId ?? ''),
    organizationName: String(claims.organizationName ?? ''),
  };
}

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  });

  if (!response.ok) {
    let message = `${init?.method ?? 'GET'} ${path} failed with ${response.status}`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // response body wasn't JSON, keep the default message
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export async function login(email: string, password: string): Promise<LoginResult> {
  const result = await request<LoginResult>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: email, password }),
  });
  if (result.authResponse) {
    setToken(result.authResponse.token);
  }
  return result;
}

export async function verifyMfaChallenge(challengeToken: string, code: string): Promise<AuthResponse> {
  const auth = await request<AuthResponse>('/api/auth/mfa/verify', {
    method: 'POST',
    body: JSON.stringify({ challengeToken, code }),
  });
  setToken(auth.token);
  return auth;
}

export function authStatus(): Promise<AuthModeStatus> {
  return request<AuthModeStatus>('/api/auth/status');
}

export async function exchangeCognitoCode(code: string, redirectUri: string): Promise<AuthResponse> {
  const auth = await request<AuthResponse>('/api/auth/cognito/exchange', {
    method: 'POST',
    body: JSON.stringify({ code, redirectUri }),
  });
  setToken(auth.token);
  return auth;
}

export async function signup(organizationName: string, email: string, password: string): Promise<AuthResponse> {
  const auth = await request<AuthResponse>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ organizationName, email, password }),
  });
  setToken(auth.token);
  return auth;
}

export function logout() {
  clearSession();
}

export async function requestPasswordReset(email: string): Promise<void> {
  await request<void>('/api/auth/password-reset/request', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function confirmPasswordReset(resetToken: string, newPassword: string): Promise<void> {
  await request<void>('/api/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token: resetToken, newPassword }),
  });
}

export const api = {
  operatorConsole: () => request<OperatorConsole>('/api/operator/console'),
  operatorErrors: () => request<ErrorEventView[]>('/api/operator/errors'),
  executiveBriefing: () => request<ExecutiveBriefing>('/api/briefing/executive'),
  engineeringDna: () => request<EngineeringDna>('/api/engineering-dna'),
  deployments: () => request<Deployment[]>('/api/deployments'),
  deployment: (id: number) => request<Deployment>(`/api/deployments/${id}`),
  incidents: () => request<Incident[]>('/api/incidents'),
  architectureBrain: () => request<ArchitectureBrain>('/api/architecture/brain'),
  auditEvents: () => request<AuditEvent[]>('/api/audit-events'),
  aiUsage: () => request<AiUsageSummary>('/api/ai/usage'),
  scanForSecrets: (content: string, filename: string) =>
    request<SecretScanResponse>('/api/security/secret-scan', {
      method: 'POST',
      body: JSON.stringify({ content, filename }),
    }),
  askCommand: (command: string, deploymentId: number | null = null) =>
    request<CommandResponse>('/api/ai/command', {
      method: 'POST',
      body: JSON.stringify({ command, deploymentId }),
    }),
  deploymentMemory: (deploymentId: number) =>
    request<DeploymentMemory>(`/api/briefing/memory/${deploymentId}`),
  prReviews: () => request<PullRequestReview[]>('/api/pr-reviews'),
  simulatePrReview: (body: PullRequestReviewRequest) =>
    request<PullRequestReview>('/api/pr-reviews/simulate', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  decidePrReview: (id: number, body: PullRequestDecisionRequest) =>
    request<PullRequestReview>(`/api/pr-reviews/${id}/decision`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateIncidentStatus: (id: number, body: IncidentStatusUpdateRequest) =>
    request<Incident>(`/api/incidents/${id}/status`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  executeRemediationStep: (id: number, step: IncidentRemediationStep, actor: string) =>
    request<Incident>(`/api/incidents/${id}/remediation-step`, {
      method: 'POST',
      body: JSON.stringify({ step, actor }),
    }),
  integrationConnections: () => request<IntegrationConnection[]>('/api/integration-connections'),
  integrationSyncHistory: () => request<IntegrationSyncEvent[]>('/api/integration-connections/sync-history'),
  installIntegration: (provider: IntegrationProvider, body: IntegrationInstallRequest) =>
    request<IntegrationConnection>(`/api/integration-connections/${provider}/install`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  syncIntegration: (id: number) =>
    request<IntegrationConnection>(`/api/integration-connections/${id}/sync`, { method: 'POST' }),
  updateIntegrationAccount: (id: number, externalAccount: string) =>
    request<IntegrationConnection>(`/api/integration-connections/${id}/account`, {
      method: 'PUT',
      body: JSON.stringify({ externalAccount }),
    }),
  disconnectIntegration: (id: number) =>
    request<IntegrationConnection>(`/api/integration-connections/${id}`, { method: 'DELETE' }),
  backgroundJobs: () => request<BackgroundJob[]>('/api/jobs'),
  retryBackgroundJob: (id: number) => request<BackgroundJob>(`/api/jobs/${id}/retry`, { method: 'POST' }),
  webhookDeliveries: () => request<WebhookDelivery[]>('/api/webhooks/deliveries'),
  replayWebhookDelivery: (id: number) =>
    request<WebhookDelivery>(`/api/webhooks/deliveries/${id}/replay`, { method: 'POST' }),
  organizationProfile: () => request<OrganizationProfile>('/api/organization/current'),
  simulateCiSignal: (body: CiSignalRequest) =>
    request<Deployment>('/api/integrations/ci/simulate', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  teamMembers: () => request<TeamMember[]>('/api/team/members'),
  inviteTeamMember: (body: TeamInviteRequest) =>
    request<TeamMember>('/api/team/invite', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateTeamMemberRole: (id: number, role: string) =>
    request<TeamMember>(`/api/team/members/${id}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    }),
  removeTeamMember: (id: number) =>
    request<void>(`/api/team/members/${id}`, { method: 'DELETE' }),
  accountProfile: () => request<AccountProfile>('/api/account/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/account/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  enrollMfa: () => request<MfaEnrollResponse>('/api/account/mfa/enroll', { method: 'POST' }),
  confirmMfa: (code: string) =>
    request<void>('/api/account/mfa/confirm', {
      method: 'POST',
      body: JSON.stringify({ code }),
    }),
  disableMfa: (password: string) =>
    request<void>('/api/account/mfa/disable', {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),
  playbooks: () => request<EngineeringPlaybook[]>('/api/playbooks'),
  backendReadiness: () => request<BackendReadinessAssessment>('/api/playbooks/backend-readiness'),
};
