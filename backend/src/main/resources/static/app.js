const state = {
  deployments: [],
  auditEvents: [],
  executiveBriefing: null,
  engineeringDna: null,
  deploymentMemory: null,
  prReviews: [],
  latestPrReview: null,
  architectureBrain: null,
  integrationConnections: [],
  integrationSyncHistory: [],
  incidents: [],
  playbooks: [],
  backendReadiness: null,
  organizationProfile: null,
  aiProviderStatus: null,
  operatorConsole: null,
  backgroundJobs: [],
  webhookDeliveries: [],
  authStatus: null,
  selectedId: null,
  auth: JSON.parse(localStorage.getItem("sentinelAuth") || "null"),
  conversationSeeded: false,
  demoProgress: JSON.parse(localStorage.getItem("sentinelDemoProgress") || "{}"),
  liveDemoRunning: false,
};

const byId = (id) => document.getElementById(id);
const demoSteps = ["signedIn", "briefing", "command", "memory", "prReview", "architecture", "decision"];
const sentinelScene = {
  canvas: null,
  context: null,
  animationId: null,
  resizeObserver: null,
  reducedMotion: window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  nodes: [],
  links: [],
  startedAt: performance.now(),
};
const escapeHtml = (value) =>
  String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

async function getJson(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(state.auth?.token ? { Authorization: `Bearer ${state.auth.token}` } : {}),
      ...(options.headers || {}),
    },
  });

  if (!response.ok) {
    let payload = null;
    try {
      payload = await response.json();
    } catch (parseError) {
      payload = null;
    }
    const message = payload?.message
      ? `${payload.message}${payload.requestId ? ` Request ID: ${payload.requestId}` : ""}`
      : `Request failed: ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.code = payload?.code;
    error.requestId = payload?.requestId || response.headers.get("X-Request-ID");
    error.details = payload?.details || {};
    throw error;
  }

  return response.json();
}

async function loadAuthStatus() {
  state.authStatus = await getJson("/api/auth/status");
}

async function handleCognitoCallback() {
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  const error = params.get("error");
  const callbackState = params.get("state");
  if (error) {
    window.history.replaceState({}, document.title, window.location.pathname);
    byId("loginError").textContent = `Cognito sign-in failed: ${error}`;
    return;
  }
  if (!code || state.authStatus?.mode !== "cognito") {
    return;
  }
  if (!callbackState || callbackState !== sessionStorage.getItem("sentinelCognitoState")) {
    window.history.replaceState({}, document.title, window.location.pathname);
    byId("loginError").textContent = "Cognito sign-in state could not be verified. Try signing in again.";
    return;
  }
  try {
    state.auth = await getJson("/api/auth/cognito/exchange", {
      method: "POST",
      body: JSON.stringify({
        code,
        redirectUri: state.authStatus.cognitoRedirectUri || window.location.origin + window.location.pathname,
      }),
    });
    sessionStorage.removeItem("sentinelCognitoState");
    localStorage.setItem("sentinelAuth", JSON.stringify(state.auth));
    window.history.replaceState({}, document.title, window.location.pathname);
  } catch (error) {
    window.history.replaceState({}, document.title, window.location.pathname);
    byId("loginError").textContent = error.message || "Cognito sign-in failed. Try signing in again.";
  }
}

async function handleIntegrationCallback() {
  const params = new URLSearchParams(window.location.search);
  const provider = params.get("integrationProvider");
  const code = params.get("code");
  const stateParam = params.get("state");
  const error = params.get("integrationError");
  if (error) {
    window.history.replaceState({}, document.title, window.location.pathname);
    addMessageBubble("sentinel", `Provider authorization failed: ${error}`);
    return;
  }
  if (!provider || !code || !state.auth?.token) {
    return;
  }
  window.history.replaceState({}, document.title, window.location.pathname);
  const connection = await installIntegration(null, provider, code, stateParam || state.auth?.tenantId || "demo");
  addMessageBubble("sentinel", `${connection.displayName} authorization completed. I encrypted the provider token and refreshed the integration proof trail.`);
}

async function loadAll() {
  if (!state.authStatus) {
    await loadAuthStatus();
  }
  renderSession();
  if (!state.auth?.token) {
    renderSignedOut();
    return;
  }

  let deployments;
  let auditEvents;
  let controls;
  let executiveBriefing;
  let engineeringDna;
  let prReviews;
  let architectureBrain;
  let integrationConnections;
  let integrationSyncHistory;
  let incidents;
  let playbooks;
  let backendReadiness;
  let organizationProfile;
  let aiProviderStatus;
  let operatorConsole;
  let backgroundJobs;
  let webhookDeliveries;
  try {
    [deployments, auditEvents, controls, executiveBriefing, engineeringDna, prReviews, architectureBrain, integrationConnections, integrationSyncHistory, incidents, playbooks, backendReadiness, organizationProfile, aiProviderStatus, operatorConsole, backgroundJobs, webhookDeliveries] = await Promise.all([
      getJson("/api/deployments"),
      getJson("/api/audit-events"),
      getJson("/api/security/aws-controls"),
      getJson("/api/briefing/executive"),
      getJson("/api/engineering-dna"),
      getJson("/api/pr-reviews"),
      getJson("/api/architecture/brain"),
      getJson("/api/integration-connections"),
      getJson("/api/integration-connections/sync-history"),
      getJson("/api/incidents"),
      getJson("/api/playbooks"),
      getJson("/api/playbooks/backend-readiness"),
      getJson("/api/organization/current"),
      getJson("/api/ai/provider"),
      getJson("/api/operator/console"),
      getJson("/api/jobs"),
      getJson("/api/webhooks/deliveries"),
    ]);
  } catch (error) {
    if ([401, 403].includes(error.status)) {
      logout();
      return;
    }
    throw error;
  }

  state.deployments = deployments;
  state.auditEvents = auditEvents;
  state.executiveBriefing = executiveBriefing;
  state.engineeringDna = engineeringDna;
  state.prReviews = prReviews;
  state.latestPrReview = prReviews[0] || null;
  state.architectureBrain = architectureBrain;
  state.integrationConnections = integrationConnections;
  state.integrationSyncHistory = integrationSyncHistory;
  state.incidents = incidents;
  state.playbooks = playbooks;
  state.backendReadiness = backendReadiness;
  state.organizationProfile = organizationProfile;
  state.aiProviderStatus = aiProviderStatus;
  state.operatorConsole = operatorConsole;
  state.backgroundJobs = backgroundJobs;
  state.webhookDeliveries = webhookDeliveries;
  renderSession();
  renderOrganizationProfile();
  renderAiProviderStatus();
  renderExecutiveBriefing();
  renderBriefingStats();
  renderEngineeringDna();
  renderDeployments();
  renderAuditEvents(auditEvents);
  renderSecurityControls(controls);
  renderPrReviewResult();
  renderIncidentCommandCenter();
  renderBackendReadiness();
  renderEngineeringPlaybooks();
  renderArchitectureBrain();
  renderIntegrationConnections();
  renderOperatorConsole();
  renderBackgroundJobs();
  renderWebhookDeliveries();
  renderCommandTelemetry();
  updateSentinel3dScene();
  markDemoStep("signedIn");

  if (!state.selectedId && deployments.length > 0) {
    await selectDeployment(deployments[0].id, false);
  } else {
    renderSelectedDeployment();
  }
  await renderMemoryTimeline();

  renderBriefingLine();
  seedConversation();
  markDemoStep("briefing");
}

function saveDemoProgress() {
  localStorage.setItem("sentinelDemoProgress", JSON.stringify(state.demoProgress));
}

function markDemoStep(step) {
  if (!demoSteps.includes(step) || state.demoProgress[step]) {
    renderDemoProgress();
    return;
  }
  state.demoProgress[step] = true;
  saveDemoProgress();
  renderDemoProgress();
}

function renderDemoProgress() {
  const completed = demoSteps.filter((step) => state.demoProgress[step]).length;
  const progressText = byId("demoProgressText");
  const progressBar = byId("demoProgressBar");
  if (progressText) progressText.textContent = `${completed}/${demoSteps.length} complete`;
  if (progressBar) progressBar.style.width = `${Math.round((completed / demoSteps.length) * 100)}%`;
  document.querySelectorAll("[data-demo-step]").forEach((item) => {
    const isComplete = Boolean(state.demoProgress[item.dataset.demoStep]);
    item.classList.toggle("complete", isComplete);
  });
}

function updateLiveDemoStatus(message = "Ready for a guided production scenario.", running = state.liveDemoRunning) {
  const status = byId("liveDemoStatus");
  const button = byId("runLiveDemoButton");
  const consolePanel = byId("briefing");
  if (status) status.textContent = message;
  if (byId("commandStatus")) {
    byId("commandStatus").textContent = running ? "Live demo running" : state.auth?.token ? "Reasoning live" : "Standing by";
  }
  if (button) {
    button.disabled = running;
    button.textContent = running ? "Running incident demo..." : "Run live incident demo";
  }
  if (consolePanel) consolePanel.classList.toggle("demo-running", running);
}

function addLiveDemoMessage(message) {
  updateLiveDemoStatus(message, true);
  addMessageBubble("sentinel", message);
}

function demoPause() {
  return new Promise((resolve) => setTimeout(resolve, sentinelScene.reducedMotion ? 90 : 540));
}

function upsertById(items, item) {
  return [item, ...items.filter((existing) => existing.id !== item.id)];
}

function renderSession() {
  const signedIn = Boolean(state.auth?.token);
  const cognitoMode = state.authStatus?.mode === "cognito";
  byId("loginPanel").classList.toggle("hidden", signedIn);
  byId("logoutButton").classList.toggle("hidden", !signedIn);
  byId("sessionUser").textContent = signedIn ? state.auth.username : cognitoMode ? "Use company SSO" : "Use a demo account";
  byId("sessionRole").textContent = signedIn ? state.auth.role.replace("_", " ") : "Signed out";
  byId("tenantBadge").textContent = signedIn
    ? `${state.auth.organizationName || "Workspace"} · ${state.auth.tenantId || "tenant"}`
    : "No workspace";
  byId("commandStatus").textContent = signedIn ? "Reasoning live" : "Standing by";
  byId("username").closest("label").classList.toggle("hidden", cognitoMode);
  byId("password").closest("label").classList.toggle("hidden", cognitoMode);
  byId("demoLoginButton").classList.toggle("hidden", cognitoMode);
  byId("cognitoLoginButton").classList.toggle("hidden", !cognitoMode);
}

function renderSignedOut() {
  state.deployments = [];
  state.auditEvents = [];
  state.executiveBriefing = null;
  state.engineeringDna = null;
  state.deploymentMemory = null;
  state.prReviews = [];
  state.latestPrReview = null;
  state.architectureBrain = null;
  state.integrationConnections = [];
  state.integrationSyncHistory = [];
  state.incidents = [];
  state.playbooks = [];
  state.backendReadiness = null;
  state.organizationProfile = null;
  state.aiProviderStatus = null;
  state.operatorConsole = null;
  state.backgroundJobs = [];
  state.webhookDeliveries = [];
  state.conversationSeeded = false;
  byId("briefingStats").innerHTML = "";
  byId("executiveMetrics").innerHTML = "";
  byId("executiveRecommendation").innerHTML = "";
  byId("engineeringDna").innerHTML = "";
  byId("memoryTimeline").innerHTML = "";
  byId("prReviewResult").className = "pr-review-result empty-state";
  byId("prReviewResult").textContent = "Simulate a PR review and Sentinel will give a merge recommendation.";
  byId("architectureBrain").className = "architecture-brain empty-state";
  byId("architectureBrain").textContent = "Sign in and Sentinel will map service fragility.";
  byId("integrationConnections").className = "integration-connections empty-state";
  byId("integrationConnections").textContent = "Sign in to connect GitHub, Jira, and CI providers.";
  byId("incidentCount").textContent = "0 active";
  byId("incidentCommandCenter").className = "incident-grid empty-state";
  byId("incidentCommandCenter").textContent = "Sign in and Sentinel will open incidents from high-risk production signals.";
  byId("playbookCount").textContent = "0 loaded";
  byId("backendReadiness").className = "backend-readiness empty-state";
  byId("backendReadiness").textContent = "Sentinel will assess this product against the backend shipping checklist.";
  byId("engineeringPlaybooks").className = "playbook-grid empty-state";
  byId("engineeringPlaybooks").textContent = "Sign in and Sentinel will load production engineering playbooks.";
  byId("operatorConsoleContent").className = "operator-console-content empty-state";
  byId("operatorConsoleContent").textContent = "Sign in as an operator and Sentinel will report production readiness.";
  byId("backgroundJobs").className = "background-jobs empty-state";
  byId("backgroundJobs").textContent = "Sign in and Sentinel will show retry, follow-up, and replay jobs.";
  byId("webhookDeliveriesList").className = "webhook-deliveries empty-state";
  byId("webhookDeliveriesList").textContent = "Signed webhook deliveries will appear here with replay state.";
  byId("deploymentList").innerHTML = "";
  byId("auditEvents").innerHTML = "";
  byId("securityControls").innerHTML = "";
  byId("conversation").innerHTML = "";
  byId("briefingLine").textContent = "Sign in and I will summarize what needs attention.";
  renderCommandTelemetry();
  byId("executiveGreeting").textContent = "Good morning.";
  byId("executiveSummary").textContent = "Sentinel is waiting for engineering signals.";
  byId("detailTitle").textContent = "No deployment selected";
  byId("deploymentDetail").className = "empty-state";
  byId("deploymentDetail").textContent = "Sign in to inspect Sentinel reasoning.";
  renderOrganizationProfile();
  renderAiProviderStatus();
  renderDemoProgress();
  updateSentinel3dScene();
}

function renderOrganizationProfile() {
  const profile = state.organizationProfile;
  byId("workspaceName").textContent = profile?.organizationName || "Sign in";
  byId("workspaceStatus").textContent = profile
    ? `${profile.workspaceStatus} · ${profile.tenantId}`
    : "Tenant context will appear here.";
  byId("onboardingSteps").innerHTML = profile?.onboardingSteps?.length
    ? profile.onboardingSteps
        .map(
          (step) => `
            <article class="${step.complete ? "complete" : ""}">
              <span></span>
              <div>
                <strong>${escapeHtml(step.label)}</strong>
                <p>${escapeHtml(step.detail)}</p>
              </div>
            </article>
          `
        )
        .join("")
    : `<div class="empty-mini">Secure a workspace to see onboarding state.</div>`;
}

function renderAiProviderStatus() {
  const status = state.aiProviderStatus;
  byId("aiProviderName").textContent = status?.activeProvider || "Provider offline";
  byId("aiProviderStatus").textContent = status
    ? `${status.model} · ${status.externalCallsEnabled ? "External calls enabled" : "Local deterministic mode"}`
    : "Sign in to inspect reasoning provider.";
}

function riskClass(level) {
  return `risk-${String(level || "low").toLowerCase()}`;
}

function riskRank(deployment) {
  return deployment.riskAssessment?.score || 0;
}

function selectedDeployment() {
  return state.deployments.find((item) => item.id === state.selectedId);
}

function highestRiskDeployment() {
  return [...state.deployments].sort((a, b) => riskRank(b) - riskRank(a))[0];
}

function orgIntelligence() {
  const total = state.deployments.length;
  const highRisk = state.deployments.filter((item) =>
    ["HIGH", "CRITICAL"].includes(item.riskAssessment?.level)
  ).length;
  const blocked = state.deployments.filter((item) => item.status === "BLOCKED").length;
  const approved = state.deployments.filter((item) => item.status === "APPROVED").length;
  const production = state.deployments.filter((item) => item.environment === "production").length;
  const avgRisk = total
    ? Math.round(state.deployments.reduce((sum, item) => sum + riskRank(item), 0) / total)
    : 0;
  const dependencyCount = state.deployments.reduce((sum, item) => sum + item.dependencies.length, 0);
  const maturity = Math.max(42, Math.min(96, 92 - avgRisk + approved * 3 - blocked * 5));
  const projectedSavings = Math.max(18000, Math.round((highRisk * 26000 + blocked * 14000 + dependencyCount * 950) / 1000) * 1000);

  return { total, highRisk, blocked, approved, production, avgRisk, dependencyCount, maturity, projectedSavings };
}

function renderExecutiveBriefing() {
  const briefing = state.executiveBriefing;
  if (!briefing) return;

  byId("executiveGreeting").textContent = briefing.greeting;
  byId("executiveSummary").textContent = briefing.summary;

  byId("executiveMetrics").innerHTML = briefing.metrics
    .map(
      (metric) => `
        <article>
          <span>${escapeHtml(metric.label)}</span>
          <strong>${escapeHtml(metric.value)}</strong>
        </article>
      `
    )
    .join("");

  byId("executiveRecommendation").innerHTML = `
      <p class="eyebrow">Sentinel recommendation</p>
      <strong>${escapeHtml(briefing.recommendationTitle)}</strong>
      <p>${escapeHtml(briefing.recommendation)}</p>
    `;
}

function renderBriefingStats() {
  const intel = orgIntelligence();

  byId("briefingStats").innerHTML = [
    ["Reviewed", intel.total],
    ["High risk", intel.highRisk],
    ["Blocked", intel.blocked],
    ["Avg risk", `${intel.avgRisk}%`],
  ]
    .map(
      ([label, value]) => `
        <article class="pulse-item">
          <span>${escapeHtml(label)}</span>
          <strong>${escapeHtml(value)}</strong>
        </article>
      `
    )
    .join("");
}

function renderEngineeringDna() {
  const dna = state.engineeringDna;
  if (!dna) return;

  byId("engineeringDna").innerHTML = dna.scores
    .map(
      (score) => `
        <article class="dna-item">
          <div>
            <span>${escapeHtml(score.label)}</span>
            <strong>${escapeHtml(score.value)}</strong>
          </div>
          <meter min="0" max="100" value="${escapeHtml(score.value)}"></meter>
        </article>
      `
    )
    .join("");
}

function renderBriefingLine() {
  const risky = highestRiskDeployment();
  if (!risky) {
    byId("briefingLine").textContent = "I do not see any active deployment reviews.";
    return;
  }
  const assessment = risky.riskAssessment;
  byId("briefingLine").textContent =
    `I reviewed ${state.deployments.length} deployments, ${state.auditEvents.length} audit events, and current AWS controls. ` +
    `${risky.serviceName} is the release I would inspect first at ${assessment.score}% ${assessment.level} risk.`;
}

function renderCommandTelemetry() {
  const risky = highestRiskDeployment();
  const failedJobs = (state.backgroundJobs || []).filter((job) => job.status === "FAILED").length;
  const failedDeliveries = (state.webhookDeliveries || []).filter((delivery) => delivery.status === "FAILED").length;
  const readinessScore = state.backendReadiness?.assessment?.overallScore;
  const telemetry = [
    {
      label: "Release graph",
      value: state.deployments?.length ? `${state.deployments.length} nodes` : "Idle",
      detail: state.deployments?.length ? "deployment context" : "awaiting signal",
      tone: "cool",
    },
    {
      label: "Highest risk",
      value: risky?.riskAssessment ? `${risky.riskAssessment.score}%` : "--",
      detail: risky?.serviceName || "no active review",
      tone: risky?.riskAssessment?.score >= 80 ? "hot" : "cool",
    },
    {
      label: "Incidents",
      value: `${state.incidents?.length || 0} active`,
      detail: state.incidents?.length ? "response open" : "clear",
      tone: state.incidents?.length ? "warm" : "cool",
    },
    {
      label: "Replay queue",
      value: `${failedJobs + failedDeliveries} issues`,
      detail: "jobs + webhooks",
      tone: failedJobs + failedDeliveries > 0 ? "warm" : "cool",
    },
    {
      label: "Backend ready",
      value: readinessScore ? `${readinessScore}%` : "--",
      detail: readinessScore ? "shipping posture" : "not assessed",
      tone: readinessScore >= 90 ? "cool" : "warm",
    },
  ];
  byId("commandTelemetry").innerHTML = telemetry
    .map((item) => `
      <article class="telemetry-card telemetry-${item.tone}">
        <span>${escapeHtml(item.label)}</span>
        <strong>${escapeHtml(item.value)}</strong>
        <small>${escapeHtml(item.detail)}</small>
      </article>
    `)
    .join("");
}

function renderDeployments() {
  byId("deploymentList").innerHTML = state.deployments
    .map((deployment) => {
      const assessment = deployment.riskAssessment;
      const selected = deployment.id === state.selectedId ? "selected" : "";

      return `
        <button class="release-row ${selected}" onclick="selectDeployment(${deployment.id})" type="button">
          <span class="risk-dot ${riskClass(assessment?.level)}"></span>
          <span>
            <strong>${escapeHtml(deployment.serviceName)}</strong>
            <small>${escapeHtml(deployment.deploymentKey)} · ${escapeHtml(deployment.ownerTeam)}</small>
          </span>
          <span class="score ${riskClass(assessment?.level)}">${escapeHtml(assessment?.score || 0)}%</span>
        </button>
      `;
    })
    .join("");
}

async function selectDeployment(id, addMessage = true) {
  state.selectedId = id;
  renderDeployments();
  renderSelectedDeployment();
  await renderMemoryTimeline();
  if (addMessage) {
    markDemoStep("memory");
    const deployment = selectedDeployment();
    addMessageBubble(
      "sentinel",
      `I am now focused on ${deployment.serviceName}. ${deployment.riskAssessment.recommendation}`
    );
  }
}

function renderSelectedDeployment() {
  const deployment = selectedDeployment();
  if (!deployment) return;

  const assessment = deployment.riskAssessment;
  const canApprove = ["ADMIN", "RELEASE_MANAGER"].includes(state.auth?.role);
  const topReasons = assessment.reasons.slice(0, 4);
  byId("detailTitle").textContent = `${deployment.deploymentKey} · ${deployment.serviceName}`;
  byId("deploymentDetail").className = "release-intelligence";
  byId("deploymentDetail").innerHTML = `
    <section class="verdict">
      <div>
        <span class="badge ${riskClass(assessment.level)}">${escapeHtml(assessment.level)}</span>
        <strong>${escapeHtml(assessment.score)}%</strong>
      </div>
      <p>${escapeHtml(assessment.aiExplanation)}</p>
    </section>

    <section class="decision-copy">
      <p class="eyebrow">My recommendation</p>
      <p>${escapeHtml(assessment.recommendation)}</p>
    </section>

    <section>
      <p class="eyebrow">Evidence I inspected</p>
      <ul class="reason-list">
        ${topReasons
          .map(
            (reason) => `
              <li>
                <span>${escapeHtml(reason.category)}</span>
                <strong class="${reason.impact >= 16 ? "risk-high-text" : "risk-medium-text"}">+${escapeHtml(reason.impact)}</strong>
                <p>${escapeHtml(reason.evidence)}</p>
              </li>
            `
          )
          .join("")}
      </ul>
    </section>

    <section>
      <p class="eyebrow">Blast radius</p>
      <div class="dependency-list">
        ${deployment.dependencies.map((item) => `<span>${escapeHtml(item)}</span>`).join("")}
      </div>
    </section>

    <section class="approval-box">
      <textarea id="approvalNote" placeholder="Approval note...">Reviewed Sentinel's release judgment.</textarea>
      <button ${canApprove ? "" : "disabled"} onclick="decide(${deployment.id}, 'APPROVE')" type="button">Approve</button>
      <button ${canApprove ? "" : "disabled"} class="ghost-button" onclick="decide(${deployment.id}, 'REQUEST_CHANGES')" type="button">Request changes</button>
      <button ${canApprove ? "" : "disabled"} class="danger-button" onclick="decide(${deployment.id}, 'BLOCK')" type="button">Block</button>
    </section>
  `;
}

async function renderMemoryTimeline() {
  const deployment = selectedDeployment();
  if (!deployment) {
    byId("memoryTimeline").innerHTML = `<div class="empty-state">Select a release and I will recall similar engineering patterns.</div>`;
    return;
  }

  state.deploymentMemory = await getJson(`/api/briefing/memory/${deployment.id}`);
  byId("memoryTimeline").innerHTML = state.deploymentMemory.events.length
    ? state.deploymentMemory.events
        .map(
          (event) => `
            <article class="memory-event">
              <span>${escapeHtml(event.date)}</span>
              <div>
                <strong>${escapeHtml(event.title)}</strong>
                <p>${escapeHtml(event.detail)}</p>
              </div>
            </article>
          `
        )
        .join("")
    : `<div class="empty-state">Select a release and I will recall similar engineering patterns.</div>`;
}

async function decide(id, decision) {
  const note = byId("approvalNote")?.value || "";
  await getJson(`/api/deployments/${id}/approval`, {
    method: "POST",
    body: JSON.stringify({
      decision,
      approver: state.auth?.username || "Release Manager",
      note,
    }),
  });
  markDemoStep("decision");
  addMessageBubble("sentinel", `Decision recorded: ${decision.replace("_", " ")}. I added this to the audit trail.`);
  await loadAll();
}

function renderAuditEvents(events) {
  byId("auditEvents").innerHTML = events
    .slice(0, 8)
    .map(
      (event) => `
        <article class="audit-event">
          <strong>${escapeHtml(event.action)}</strong>
          <span>${escapeHtml(event.actor)} · ${escapeHtml(event.target)}</span>
          <p>${escapeHtml(event.details)}</p>
        </article>
      `
    )
    .join("");
}

function renderSecurityControls(controls) {
  byId("securityControls").innerHTML = Object.entries(controls)
    .map(
      ([category, items]) => `
        <article class="security-card">
          <strong>${escapeHtml(category.replace(/([A-Z])/g, " $1"))}</strong>
          <p>${items.map(escapeHtml).join(" · ")}</p>
        </article>
      `
    )
    .join("");
}

function recommendationClass(recommendation) {
  if (recommendation === "MERGE") return "risk-low";
  if (recommendation === "WAIT") return "risk-medium";
  return "risk-high";
}

function renderPrReviewResult() {
  const review = state.latestPrReview;
  if (!review) {
    byId("prReviewResult").className = "pr-review-result empty-state";
    byId("prReviewResult").textContent = "Simulate a PR review and Sentinel will give a merge recommendation.";
    return;
  }

  byId("prReviewResult").className = "pr-review-result";
  byId("prReviewResult").innerHTML = `
    <section class="pr-verdict">
      <div>
        <span class="badge ${recommendationClass(review.recommendation)}">${escapeHtml(review.recommendation)}</span>
        <strong>${escapeHtml(review.riskScore)}%</strong>
      </div>
      <p>${escapeHtml(review.explanation)}</p>
    </section>
    <section>
      <p class="eyebrow">Changed files</p>
      <div class="file-list">
        ${review.changedFiles.map((file) => `<span>${escapeHtml(file)}</span>`).join("")}
      </div>
    </section>
    <section class="pr-actions">
      <button type="button" onclick="decidePrReview(${review.id}, 'MERGED')">Merge</button>
      <button type="button" class="ghost-button" onclick="decidePrReview(${review.id}, 'WAITING')">Wait</button>
      <button type="button" class="danger-button" onclick="decidePrReview(${review.id}, 'BLOCKED')">Block</button>
    </section>
    ${review.decision ? `<p class="decision-note">Decision recorded: ${escapeHtml(review.decision)}</p>` : ""}
  `;
}

function renderIncidentCommandCenter() {
  const incidents = state.incidents || [];
  byId("incidentCount").textContent = `${incidents.length} active`;
  const container = byId("incidentCommandCenter");
  if (!incidents.length) {
    container.className = "incident-grid empty-state";
    container.textContent = "No active incidents. Sentinel is still watching high-risk production signals.";
    return;
  }

  container.className = "incident-grid";
  container.innerHTML = incidents.map((incident) => `
    <article class="incident-card">
      <section class="incident-main">
        <div class="incident-title-row">
          <span class="badge ${incidentSeverityClass(incident.severity)}">${escapeHtml(incident.severity)}</span>
          <span class="badge ${incidentStatusClass(incident.status)}">${escapeHtml(incident.status.replace("_", " "))}</span>
          <code>${escapeHtml(incident.incidentKey)}</code>
        </div>
        <h3>${escapeHtml(incident.serviceName)} · ${escapeHtml(incident.ownerTeam)}</h3>
        <p>${escapeHtml(incident.commanderBrief)}</p>
        <div class="incident-impact">
          <span>Risk ${escapeHtml(incident.riskScore)}%</span>
          <span>${escapeHtml(incident.environment)}</span>
          <span>${escapeHtml(incident.deploymentKey)}</span>
        </div>
        <section class="incident-action">
          <p class="eyebrow">AI recommended next action</p>
          <strong>${escapeHtml(incident.recommendedAction)}</strong>
        </section>
        <section class="incident-systems">
          <p class="eyebrow">Affected systems</p>
          <p>${escapeHtml(incident.affectedSystems)}</p>
        </section>
      </section>
      <section class="incident-response">
        <p class="eyebrow">Response timeline</p>
        <div class="incident-timeline">
          ${(incident.timeline || []).slice(-5).reverse().map((event) => `
            <div>
              <strong>${escapeHtml(event.label)}</strong>
              <span>${escapeHtml(event.actor)} · ${escapeHtml(new Date(event.occurredAt).toLocaleString())}</span>
              <p>${escapeHtml(event.detail)}</p>
            </div>
          `).join("")}
        </div>
        <div class="incident-buttons">
          <button type="button" class="ghost-button" onclick="updateIncidentStatus(${incident.id}, 'INVESTIGATING')">Investigate</button>
          <button type="button" class="ghost-button" onclick="updateIncidentStatus(${incident.id}, 'MITIGATING')">Mitigate</button>
          <button type="button" onclick="updateIncidentStatus(${incident.id}, 'RESOLVED')">Resolve</button>
        </div>
      </section>
    </article>
  `).join("");
}

function incidentSeverityClass(severity) {
  if (severity === "SEV1" || severity === "SEV2") return "risk-high";
  return "risk-medium";
}

function incidentStatusClass(status) {
  if (status === "RESOLVED") return "risk-low";
  if (status === "MITIGATING") return "risk-medium";
  return "risk-high";
}

async function updateIncidentStatus(id, status) {
  await getJson(`/api/incidents/${id}/status`, {
    method: "POST",
    body: JSON.stringify({
      status,
      actor: state.auth?.username || "release@sentinel.ai",
      note: `Operator moved incident to ${status.toLowerCase().replace("_", " ")} from the command center.`,
    }),
  });
  addMessageBubble("sentinel", `Incident status updated to ${status.replace("_", " ")}. I added it to the response timeline and audit trail.`);
  await loadAll();
}

function renderEngineeringPlaybooks() {
  const playbooks = state.playbooks || [];
  byId("playbookCount").textContent = `${playbooks.length} loaded`;
  const container = byId("engineeringPlaybooks");
  if (!playbooks.length) {
    container.className = "playbook-grid empty-state";
    container.textContent = "Sign in and Sentinel will load production engineering playbooks.";
    return;
  }

  container.className = "playbook-grid";
  container.innerHTML = playbooks.map((playbook) => `
    <article class="playbook-card">
      <div>
        <p class="eyebrow">${escapeHtml(playbook.category)}</p>
        <h3>${escapeHtml(playbook.title)}</h3>
        <p>${escapeHtml(playbook.summary)}</p>
      </div>
      <section>
        <p class="eyebrow">Readiness checks</p>
        <ul>
          ${playbook.checks.slice(0, 3).map((check) => `<li>${escapeHtml(check)}</li>`).join("")}
        </ul>
      </section>
      <section>
        <p class="eyebrow">Sentinel action</p>
        <p>${escapeHtml(playbook.sentinelActions[0] || "Use this playbook to tighten release judgment.")}</p>
      </section>
      <button type="button" class="ghost-button" onclick="askPlaybook('${escapeHtml(playbook.id)}')">Ask Sentinel</button>
    </article>
  `).join("");
}

function renderBackendReadiness() {
  const assessment = state.backendReadiness;
  const container = byId("backendReadiness");
  if (!assessment) {
    container.className = "backend-readiness empty-state";
    container.textContent = "Sentinel will assess this product against the backend shipping checklist.";
    return;
  }

  const implemented = assessment.checks.filter((check) => check.status === "IMPLEMENTED").length;
  const partial = assessment.checks.filter((check) => check.status === "PARTIAL").length;
  const planned = assessment.checks.filter((check) => check.status === "PLANNED").length;
  container.className = "backend-readiness";
  container.innerHTML = `
    <section class="readiness-score">
      <div>
        <p class="eyebrow">Sentinel self-assessment</p>
        <strong>${escapeHtml(assessment.overallScore)}%</strong>
        <span>${escapeHtml(assessment.maturityLevel)}</span>
      </div>
      <p>${escapeHtml(assessment.summary)}</p>
    </section>
    <section class="readiness-breakdown">
      <span>${implemented} implemented</span>
      <span>${partial} partial</span>
      <span>${planned} planned</span>
    </section>
    <section class="readiness-checks">
      ${assessment.checks.map((check) => `
        <article>
          <div>
            <strong>${escapeHtml(check.category)}</strong>
            <span class="badge ${readinessStatusClass(check.status)}">${escapeHtml(check.status)}</span>
          </div>
          <meter min="0" max="100" value="${escapeHtml(check.score)}"></meter>
          <p>${escapeHtml(check.evidence)}</p>
          <small>Gap: ${escapeHtml(check.gap)}</small>
        </article>
      `).join("")}
    </section>
    <section class="readiness-actions">
      <p class="eyebrow">Next build actions</p>
      ${assessment.nextActions.map((action) => `<span>${escapeHtml(action)}</span>`).join("")}
    </section>
  `;
}

function readinessStatusClass(status) {
  if (status === "IMPLEMENTED") return "risk-low";
  if (status === "PARTIAL") return "risk-medium";
  return "risk-high";
}

function askPlaybook(playbookId) {
  const playbook = (state.playbooks || []).find((item) => item.id === playbookId);
  if (!playbook) return;
  byId("commandInput").value = `Use the ${playbook.title} playbook for the selected release. What should we check next?`;
  byId("commandForm").requestSubmit();
}

function severityClass(severity) {
  if (severity === "CRITICAL" || severity === "HIGH") return "risk-high";
  if (severity === "MEDIUM") return "risk-medium";
  return "risk-low";
}

function renderArchitectureBrain() {
  const brain = state.architectureBrain;
  if (!brain) {
    byId("architectureBrain").className = "architecture-brain empty-state";
    byId("architectureBrain").textContent = "Sign in and Sentinel will map service fragility.";
    return;
  }

  byId("architectureBrain").className = "architecture-brain";
  byId("architectureBrain").innerHTML = `
    <section class="architecture-summary">
      <div>
        <span>Services</span>
        <strong>${escapeHtml(brain.serviceCount)}</strong>
      </div>
      <div>
        <span>Dependencies</span>
        <strong>${escapeHtml(brain.dependencyCount)}</strong>
      </div>
      <div>
        <span>Risks</span>
        <strong>${escapeHtml(brain.riskCount)}</strong>
      </div>
    </section>
    <section class="decision-copy">
      <p class="eyebrow">Sentinel architecture judgment</p>
      <p>${escapeHtml(brain.summary)}</p>
      <p>${escapeHtml(brain.recommendedRefactor)}</p>
    </section>
    <section class="architecture-grid">
      <div>
        <p class="eyebrow">High-risk findings</p>
        <div class="architecture-list">
          ${brain.risks.slice(0, 5).map((risk) => `
            <article>
              <span class="badge ${severityClass(risk.severity)}">${escapeHtml(risk.severity)}</span>
              <strong>${escapeHtml(risk.serviceName)} · ${escapeHtml(risk.riskType.replaceAll("_", " "))}</strong>
              <p>${escapeHtml(risk.explanation)}</p>
            </article>
          `).join("")}
        </div>
      </div>
      <div>
        <p class="eyebrow">Service map</p>
        <div class="architecture-list">
          ${brain.services.slice(0, 6).map((service) => `
            <article>
              <strong>${escapeHtml(service.serviceName)}</strong>
              <p>${escapeHtml(service.ownerTeam)} · ${escapeHtml(service.runtime)} · ${escapeHtml(service.tier)}</p>
            </article>
          `).join("")}
        </div>
      </div>
    </section>
  `;
}

function statusClass(status) {
  if (["CONNECTED", "SUCCESS", "SUCCEEDED", "REPLAYED"].includes(status)) return "risk-low";
  if (["NEEDS_ATTENTION", "QUEUED", "RUNNING", "CANCELLED", "RECEIVED", "REPLAY_QUEUED"].includes(status)) return "risk-medium";
  return "risk-high";
}

function providerAccountPlaceholder(provider) {
  const org = state.auth?.organizationName || "Customer";
  const slug = org.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  if (provider === "GITHUB") return `${slug}/engineering`;
  if (provider === "JIRA") return `${slug}.atlassian.net`;
  return `${org} delivery pipelines`;
}

function renderIntegrationConnections() {
  const connections = state.integrationConnections || [];
  if (!connections.length) {
    byId("integrationConnections").className = "integration-connections empty-state";
    byId("integrationConnections").textContent = "Sign in to connect GitHub, Jira, and CI providers.";
    return;
  }

  byId("integrationConnections").className = "integration-connections";
  byId("integrationConnections").innerHTML = connections
    .map(
      (connection) => `
        <article class="integration-card">
          <div class="integration-card-header">
            <div>
              <p class="eyebrow">${escapeHtml(connection.provider)}</p>
              <h3>${escapeHtml(connection.displayName)}</h3>
            </div>
            <span class="badge ${statusClass(connection.status)}">${escapeHtml(connection.status.replace("_", " "))}</span>
          </div>
          <p>${escapeHtml(connection.statusDetail)}</p>
          <meter min="0" max="100" value="${escapeHtml(connection.healthScore || 0)}"></meter>
          <div class="integration-meta">
            <span>Scopes: ${escapeHtml(connection.scopes)}</span>
            <span>Token ref: ${escapeHtml(connection.tokenSecretRef)}</span>
            <span>Account: ${escapeHtml(connection.externalAccount || "Not connected")}</span>
            <span>Health: ${escapeHtml(connection.healthScore || 0)}% · ${escapeHtml(connection.lastSyncStatus || "Not synced")}</span>
            <span>Last sync: ${escapeHtml(connection.lastSyncAt ? new Date(connection.lastSyncAt).toLocaleString() : "Never")}</span>
          </div>
          <div class="integration-actions">
            <a class="ghost-link" href="${escapeHtml(connection.installUrl)}" target="_blank" rel="noreferrer">Provider install</a>
            <button type="button" onclick="installIntegration(${connection.id}, '${connection.provider}')">Connect</button>
            <button type="button" class="ghost-button" onclick="syncIntegration(${connection.id})">Sync</button>
            <button type="button" class="danger-button" onclick="disconnectIntegration(${connection.id})">Disconnect</button>
          </div>
        </article>
      `
    )
    .join("") + renderIntegrationSyncHistory();
}

function renderOperatorConsole() {
  const consoleState = state.operatorConsole;
  const container = byId("operatorConsoleContent");
  if (!consoleState) {
    container.className = "operator-console-content empty-state";
    container.textContent = "Sign in as an operator and Sentinel will report production readiness.";
    return;
  }

  const metrics = consoleState.metrics || {};
  const failures = consoleState.recentFailures || [];
  const readiness = consoleState.readinessStatus || "unknown";
  const jobs = consoleState.backgroundJobs || {};
  container.className = "operator-console-content";
  container.innerHTML = `
    <div class="operator-kpi-grid">
      ${operatorKpi("Readiness", readiness, `${consoleState.requestId || "no request id"} · ${consoleState.organizationName}`)}
      ${operatorKpi("Runtime", consoleState.runtimeMode, `API ${consoleState.apiEnabled ? "on" : "off"} · Worker ${consoleState.workerEnabled ? "on" : "off"}`)}
      ${operatorKpi("Auth", consoleState.authMode, consoleState.cognitoConfigured ? "Cognito configured" : "Demo mode or Cognito pending")}
      ${operatorKpi("AI", consoleState.aiProvider, `${consoleState.configuredAiProvider} · ${consoleState.aiModel}`)}
      ${operatorKpi("Integrations", consoleState.integrationMode, `${consoleState.connectedIntegrationCount} connected · ${consoleState.attentionIntegrationCount} need attention`)}
      ${operatorKpi("Rate limits", consoleState.rateLimitBackend, `${consoleState.rateLimitPerMinute}/min · Redis ${consoleState.redisRateLimitingEnabled ? "enabled" : "disabled"}`)}
      ${operatorKpi("Jobs", `${jobs.queued || 0} queued`, `${jobs.running || 0} running · ${jobs.failed || 0} failed · ${jobs.succeeded || 0} done`)}
      ${operatorKpi("Webhooks", `${consoleState.failedWebhookDeliveryCount || 0} failed`, "Dead-letter replay enabled")}
    </div>
    <div class="operator-runtime-grid">
      <section class="operator-metrics">
        <p class="eyebrow">Operational counters</p>
        ${Object.entries(metrics).map(([name, value]) => `
          <div class="metric-row">
            <span>${escapeHtml(metricLabel(name))}</span>
            <strong>${escapeHtml(Math.round(value))}</strong>
          </div>
        `).join("")}
      </section>
      <section class="operator-failures">
        <p class="eyebrow">Recent provider failures</p>
        ${failures.length ? failures.map((failure) => `
          <article class="operator-failure">
            <div>
              <strong>${escapeHtml(failure.provider)} · ${escapeHtml(failure.category)}</strong>
              <span>${escapeHtml(failure.message)}</span>
            </div>
            <code>${escapeHtml(failure.requestId || "not captured")}</code>
          </article>
        `).join("") : `<div class="operator-clear-state">No degraded provider syncs in the latest window.</div>`}
      </section>
    </div>
  `;
}

function renderBackgroundJobs() {
  const jobs = state.backgroundJobs || [];
  const container = byId("backgroundJobs");
  if (!jobs.length) {
    container.className = "background-jobs empty-state";
    container.textContent = "No durable jobs yet. Provider failures and open incidents will create retry and follow-up work here.";
    return;
  }

  container.className = "background-jobs";
  container.innerHTML = jobs.map((job) => `
    <article class="job-card">
      <div>
        <span class="badge ${statusClass(job.status)}">${escapeHtml(job.status)}</span>
        <strong>${escapeHtml(job.jobType?.replaceAll("_", " ") || "Background job")}</strong>
        <p>${escapeHtml(job.targetLabel)} · ${escapeHtml(job.targetType)} #${escapeHtml(job.targetId)}</p>
      </div>
      <div class="job-meta">
        <span>${escapeHtml(job.attempts)}/${escapeHtml(job.maxAttempts)} attempts</span>
        <span>Next ${escapeHtml(formatDate(job.nextRunAt))}</span>
        ${job.lastError ? `<code>${escapeHtml(job.lastError)}</code>` : ""}
      </div>
      ${job.status === "FAILED" ? `<button type="button" class="ghost-button" onclick="retryBackgroundJob(${job.id})">Retry</button>` : ""}
    </article>
  `).join("");
}

function renderWebhookDeliveries() {
  const deliveries = state.webhookDeliveries || [];
  const container = byId("webhookDeliveriesList");
  if (!deliveries.length) {
    container.className = "webhook-deliveries empty-state";
    container.textContent = "No signed webhook deliveries yet. Production GitHub webhooks will be recorded here.";
    return;
  }

  container.className = "webhook-deliveries";
  container.innerHTML = deliveries.map((delivery) => `
    <article class="delivery-card">
      <div>
        <span class="badge ${statusClass(delivery.status)}">${escapeHtml(delivery.status?.replaceAll("_", " ") || "UNKNOWN")}</span>
        <strong>${escapeHtml(delivery.provider)} · ${escapeHtml(delivery.eventType)}</strong>
        <p>${escapeHtml(delivery.externalDeliveryId)} · request ${escapeHtml(delivery.requestId)}</p>
      </div>
      <div class="job-meta">
        <span>${escapeHtml(delivery.targetReference || "No deployment yet")}</span>
        <span>${escapeHtml(delivery.replayAttempts)} / ${escapeHtml(delivery.maxReplayAttempts)} replay attempts</span>
        <span>${escapeHtml(delivery.replayEligibility || "ready")}${delivery.nextReplayAt ? ` · ${escapeHtml(formatDate(delivery.nextReplayAt))}` : ""}</span>
        <span>Expires ${escapeHtml(formatDate(delivery.expiresAt))}</span>
        ${delivery.failureReason ? `<code>${escapeHtml(delivery.failureReason)}</code>` : ""}
      </div>
      <button type="button" class="ghost-button" ${delivery.replayEligibility !== "ready" ? "disabled" : ""} onclick="replayWebhookDelivery(${delivery.id})">Replay</button>
    </article>
  `).join("");
}

function operatorKpi(label, value, detail) {
  const className = String(value || "").toLowerCase().includes("required") || String(value || "").toLowerCase().includes("attention")
    ? "risk-medium"
    : "risk-low";
  return `
    <article class="operator-kpi">
      <span>${escapeHtml(label)}</span>
      <strong class="${className}">${escapeHtml(value || "Unknown")}</strong>
      <small>${escapeHtml(detail || "")}</small>
    </article>
  `;
}

function metricLabel(name) {
  return String(name)
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (char) => char.toUpperCase());
}

function formatDate(value) {
  if (!value) return "not scheduled";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function startSentinel3dScene() {
  const canvas = byId("sentinel3dScene");
  if (!canvas) return;
  sentinelScene.canvas = canvas;
  sentinelScene.context = canvas.getContext("2d");
  resizeSentinel3dScene();
  if ("ResizeObserver" in window) {
    sentinelScene.resizeObserver = new ResizeObserver(resizeSentinel3dScene);
    sentinelScene.resizeObserver.observe(canvas);
  } else {
    window.addEventListener("resize", resizeSentinel3dScene);
  }
  window.matchMedia("(prefers-reduced-motion: reduce)").addEventListener("change", (event) => {
    sentinelScene.reducedMotion = event.matches;
    if (!event.matches && !sentinelScene.animationId) {
      renderSentinel3dScene();
    } else {
      renderSentinel3dScene();
    }
  });
  updateSentinel3dScene();
  if (sentinelScene.animationId) cancelAnimationFrame(sentinelScene.animationId);
  renderSentinel3dScene();
}

function resizeSentinel3dScene() {
  const canvas = sentinelScene.canvas;
  if (!canvas) return;
  const rect = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.max(1, Math.floor(rect.width * ratio));
  canvas.height = Math.max(1, Math.floor(rect.height * ratio));
  canvas.style.width = `${rect.width}px`;
  canvas.style.height = `${rect.height}px`;
  sentinelScene.context?.setTransform(ratio, 0, 0, ratio, 0, 0);
  if (sentinelScene.reducedMotion) {
    renderSentinel3dScene();
  }
}

function updateSentinel3dScene() {
  if (!sentinelScene.canvas) return;
  const deployments = (state.deployments || []).slice(0, 7);
  const incidents = (state.incidents || []).slice(0, 4);
  const jobs = (state.backgroundJobs || []).slice(0, 5);
  const deliveries = (state.webhookDeliveries || []).slice(0, 4);
  const source = [
    ...deployments.map((item, index) => sceneNode(`DEP-${item.id}`, "deployment", riskRank(item), index)),
    ...incidents.map((item, index) => sceneNode(`INC-${item.id}`, "incident", item.riskScore || 80, index + 8)),
    ...jobs.map((item, index) => sceneNode(`JOB-${item.id}`, "job", item.status === "FAILED" ? 90 : 42, index + 13)),
    ...deliveries.map((item, index) => sceneNode(`DEL-${item.id}`, "delivery", item.status === "FAILED" ? 88 : 38, index + 18)),
  ];
  sentinelScene.nodes = source.length ? source : Array.from({ length: 12 }, (_, index) => sceneNode(`idle-${index}`, "idle", 22, index));
  sentinelScene.links = sentinelScene.nodes.map((node, index) => [node, sentinelScene.nodes[(index + 3) % sentinelScene.nodes.length]]);
}

function sceneNode(id, type, risk, index) {
  const ring = 110 + (index % 4) * 34;
  const angle = index * 1.94;
  return {
    id,
    type,
    risk,
    x: Math.cos(angle) * ring,
    y: ((index % 5) - 2) * 42,
    z: Math.sin(angle) * ring,
    phase: index * 0.73,
  };
}

function renderSentinel3dScene() {
  const canvas = sentinelScene.canvas;
  const context = sentinelScene.context;
  if (!canvas || !context) return;
  const width = canvas.clientWidth;
  const height = canvas.clientHeight;
  const elapsed = (performance.now() - sentinelScene.startedAt) / 1000;
  const time = sentinelScene.reducedMotion ? 0.8 : elapsed;
  context.clearRect(0, 0, width, height);
  context.fillStyle = "rgba(2, 6, 23, 0.18)";
  context.fillRect(0, 0, width, height);

  const projected = sentinelScene.nodes.map((node) => projectSceneNode(node, time, width, height));
  sentinelScene.links.forEach(([left, right]) => {
    const a = projected.find((item) => item.id === left.id);
    const b = projected.find((item) => item.id === right.id);
    if (!a || !b) return;
    context.beginPath();
    context.moveTo(a.x, a.y);
    context.lineTo(b.x, b.y);
    context.strokeStyle = `rgba(125, 211, 252, ${0.08 + Math.min(a.scale, b.scale) * 0.13})`;
    context.lineWidth = Math.max(1, Math.min(a.scale, b.scale) * 1.6);
    context.stroke();
  });

  projected.sort((a, b) => a.scale - b.scale).forEach((node) => {
    const radius = 3 + node.scale * 8;
    const gradient = context.createRadialGradient(node.x, node.y, 1, node.x, node.y, radius * 2.5);
    gradient.addColorStop(0, node.color);
    gradient.addColorStop(1, "rgba(2, 6, 23, 0)");
    context.fillStyle = gradient;
    context.beginPath();
    context.arc(node.x, node.y, radius * 2.5, 0, Math.PI * 2);
    context.fill();
    context.strokeStyle = "rgba(226, 246, 255, 0.72)";
    context.lineWidth = 1;
    context.beginPath();
    context.arc(node.x, node.y, radius, 0, Math.PI * 2);
    context.stroke();
  });

  sentinelScene.animationId = sentinelScene.reducedMotion ? null : requestAnimationFrame(renderSentinel3dScene);
}

function projectSceneNode(node, time, width, height) {
  const rotation = time * 0.22;
  const drift = Math.sin(time * 1.4 + node.phase) * 10;
  const cos = Math.cos(rotation);
  const sin = Math.sin(rotation);
  const x = node.x * cos - node.z * sin;
  const z = node.x * sin + node.z * cos + 360;
  const y = node.y + drift;
  const scale = 360 / Math.max(180, z);
  const color = node.risk >= 80
    ? "rgba(248, 113, 113, 0.92)"
    : node.risk >= 55
      ? "rgba(251, 191, 36, 0.9)"
      : node.type === "job" || node.type === "delivery"
        ? "rgba(45, 212, 191, 0.9)"
        : "rgba(96, 165, 250, 0.9)";
  return {
    id: node.id,
    x: width / 2 + x * scale,
    y: height / 2 + y * scale,
    scale,
    color,
  };
}

async function refreshBackgroundJobs() {
  state.backgroundJobs = await getJson("/api/jobs");
  renderBackgroundJobs();
  renderCommandTelemetry();
  updateSentinel3dScene();
}

async function retryBackgroundJob(id) {
  await getJson(`/api/jobs/${id}/retry`, { method: "POST" });
  await loadAll();
}

async function refreshWebhookDeliveries() {
  state.webhookDeliveries = await getJson("/api/webhooks/deliveries");
  renderWebhookDeliveries();
  renderCommandTelemetry();
  updateSentinel3dScene();
}

async function replayWebhookDelivery(id) {
  await getJson(`/api/webhooks/deliveries/${id}/replay`, { method: "POST" });
  await loadAll();
}

async function refreshIntegrations() {
  [state.integrationConnections, state.integrationSyncHistory] = await Promise.all([
    getJson("/api/integration-connections"),
    getJson("/api/integration-connections/sync-history"),
  ]);
  renderIntegrationConnections();
}

function renderIntegrationSyncHistory() {
  const events = state.integrationSyncHistory || [];
  if (!events.length) return "";
  return `
    <section class="sync-history">
      <header class="section-heading compact">
        <p class="eyebrow">Sync history</p>
        <h2>Recent provider health</h2>
      </header>
      <div class="sync-history-list">
        ${events.slice(0, 8).map((event) => `
          <article>
            <span class="badge ${statusClass(event.status)}">${escapeHtml(event.status)}</span>
            <strong>${escapeHtml(event.provider)} · ${escapeHtml(event.healthScore)}%</strong>
            <p>${escapeHtml(event.detail)} ${escapeHtml(event.recordsInspected)} records · ${escapeHtml(event.latencyMs)}ms</p>
          </article>
        `).join("")}
      </div>
    </section>
  `;
}

function seedConversation() {
  if (state.conversationSeeded) return;
  state.conversationSeeded = true;
  byId("conversation").innerHTML = "";
  addMessageBubble("sentinel", chiefBriefing());
}

function chiefBriefing() {
  return state.executiveBriefing?.chiefBriefing ||
    "I do not see active deployment reviews. Connect GitHub or simulate a PR signal and I will start reasoning over it.";
}

function addMessageBubble(author, text) {
  const conversation = byId("conversation");
  conversation.insertAdjacentHTML(
    "beforeend",
    `
      <article class="message ${author}">
        <span>${author === "sentinel" ? "Sentinel" : "You"}</span>
        <p>${escapeHtml(text)}</p>
      </article>
    `
  );
  conversation.scrollTop = conversation.scrollHeight;
}

async function answerCommand(command) {
  const response = await getJson("/api/ai/command", {
    method: "POST",
    body: JSON.stringify({
      command,
      deploymentId: state.selectedId,
    }),
  });
  return response.answer;
}

async function login(event) {
  event.preventDefault();
  if (state.authStatus?.mode === "cognito") {
    startCognitoLogin();
    return;
  }
  byId("loginError").textContent = "";
  try {
    state.auth = await getJson("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: byId("username").value,
        password: byId("password").value,
      }),
    });
    localStorage.setItem("sentinelAuth", JSON.stringify(state.auth));
    markDemoStep("signedIn");
    await loadAll();
  } catch (error) {
    byId("loginError").textContent = error.message || "Sign-in failed. Check the demo credentials and try again.";
  }
}

function startCognitoLogin() {
  byId("loginError").textContent = "";
  if (!state.authStatus?.cognitoLoginUrl) {
    byId("loginError").textContent = "Cognito is not configured yet.";
    return;
  }
  const loginState = randomState();
  sessionStorage.setItem("sentinelCognitoState", loginState);
  const loginUrl = new URL(state.authStatus.cognitoLoginUrl);
  loginUrl.searchParams.set("state", loginState);
  window.location.assign(loginUrl.toString());
}

function randomState() {
  const bytes = new Uint8Array(16);
  window.crypto.getRandomValues(bytes);
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

function logout() {
  const logoutUrl = state.authStatus?.mode === "cognito" ? state.authStatus.cognitoLogoutUrl : "";
  state.auth = null;
  state.selectedId = null;
  state.conversationSeeded = false;
  localStorage.removeItem("sentinelAuth");
  if (logoutUrl) {
    window.location.assign(logoutUrl);
    return;
  }
  loadAll();
}

async function simulateWebhook(event) {
  event.preventDefault();
  await getJson("/api/webhooks/github/simulate", {
    method: "POST",
    body: JSON.stringify({
      repository: byId("repo").value,
      serviceName: byId("serviceName").value,
      ownerTeam: byId("ownerTeam").value,
      environment: byId("environment").value,
      commitSha: byId("commitSha").value,
      pullRequestTitle: byId("pullRequestTitle").value,
      actor: state.auth?.username,
      ciStatus: byId("ciStatus").value,
      changedFiles: byId("changedFiles").value.split("\n").map((item) => item.trim()).filter(Boolean),
      dependencies: ["checkout-service", "billing-service", "customer-ledger"],
    }),
  });
  state.selectedId = null;
  state.conversationSeeded = false;
  await loadAll();
  addMessageBubble("sentinel", "I ingested the GitHub signal and created a fresh deployment review.");
}

async function ingestCiSignal(event) {
  event.preventDefault();
  const deployment = await getJson("/api/integrations/ci/simulate", {
    method: "POST",
    body: JSON.stringify({
      provider: byId("ciProvider").value,
      repository: byId("ciRepository").value,
      serviceName: byId("ciServiceName").value,
      ownerTeam: byId("ciOwnerTeam").value,
      environment: byId("ciEnvironment").value,
      commitSha: byId("ciCommitSha").value,
      pipelineName: byId("ciPipelineName").value,
      status: byId("ciSignalStatus").value,
      failedTests: Number(byId("ciFailedTests").value),
      coverageDelta: Number(byId("ciCoverageDelta").value),
      actor: state.auth?.username,
      failedSuites: byId("ciFailedSuites").value.split("\n").map((item) => item.trim()).filter(Boolean),
      dependencies: ["checkout-service", "billing-service", "customer-ledger"],
    }),
  });
  state.selectedId = deployment.id;
  state.conversationSeeded = false;
  await loadAll();
  addMessageBubble("sentinel", `I attached CI evidence to ${deployment.serviceName}. Risk is now ${deployment.riskAssessment.score}% ${deployment.riskAssessment.level}.`);
}

async function ingestJiraSignal(event) {
  event.preventDefault();
  const deployment = await getJson("/api/integrations/jira/simulate", {
    method: "POST",
    body: JSON.stringify({
      issueKey: byId("jiraIssueKey").value,
      summary: byId("jiraSummary").value,
      priority: byId("jiraPriority").value,
      status: byId("jiraStatus").value,
      issueType: byId("jiraIssueType").value,
      serviceName: byId("jiraServiceName").value,
      ownerTeam: byId("jiraOwnerTeam").value,
      environment: byId("jiraEnvironment").value,
      commitSha: byId("jiraCommitSha").value,
      actor: state.auth?.username,
      labels: byId("jiraLabels").value.split("\n").map((item) => item.trim()).filter(Boolean),
      dependencies: ["checkout-service", "billing-service", "customer-ledger"],
    }),
  });
  state.selectedId = deployment.id;
  state.conversationSeeded = false;
  await loadAll();
  addMessageBubble("sentinel", `I linked Jira work context to ${deployment.serviceName}. Risk is now ${deployment.riskAssessment.score}% ${deployment.riskAssessment.level}.`);
}

async function simulatePrReview(event) {
  event.preventDefault();
  const review = await getJson("/api/pr-reviews/simulate", {
    method: "POST",
    body: JSON.stringify({
      repository: byId("prRepository").value,
      prNumber: Number(byId("prNumber").value),
      title: byId("prTitle").value,
      author: byId("prAuthor").value,
      serviceName: byId("prServiceName").value,
      ownerTeam: byId("prOwnerTeam").value,
      ciStatus: byId("prCiStatus").value,
      changedFiles: byId("prChangedFiles").value.split("\n").map((item) => item.trim()).filter(Boolean),
    }),
  });
  state.latestPrReview = review;
  state.prReviews = [review, ...state.prReviews.filter((item) => item.id !== review.id)];
  renderPrReviewResult();
  markDemoStep("prReview");
  addMessageBubble("sentinel", `AI Engineer reviewed PR #${review.prNumber}: ${review.recommendation} at ${review.riskScore}% risk.`);
}

async function decidePrReview(id, decision) {
  const review = await getJson(`/api/pr-reviews/${id}/decision`, {
    method: "POST",
    body: JSON.stringify({
      decision,
      actor: state.auth?.username,
      note: `AI Engineer decision recorded as ${decision}.`,
    }),
  });
  state.latestPrReview = review;
  state.prReviews = [review, ...state.prReviews.filter((item) => item.id !== review.id)];
  renderPrReviewResult();
  markDemoStep("decision");
  addMessageBubble("sentinel", `PR #${review.prNumber} decision recorded: ${decision}. I added it to engineering memory.`);
}

async function refreshArchitectureBrain() {
  state.architectureBrain = await getJson("/api/architecture/brain");
  renderArchitectureBrain();
  markDemoStep("architecture");
  addMessageBubble("sentinel", state.architectureBrain.summary + " " + state.architectureBrain.recommendedRefactor);
}

async function installIntegration(id, provider, code = null, oauthState = null) {
  const connection = await getJson(`/api/integration-connections/${provider}/install`, {
    method: "POST",
    body: JSON.stringify({
      externalAccount: providerAccountPlaceholder(provider),
      code: code || `demo-code-${id}`,
      state: oauthState || state.auth?.tenantId || "demo",
    }),
  });
  state.integrationConnections = state.integrationConnections.map((item) => item.id === connection.id ? connection : item);
  renderIntegrationConnections();
  await loadAll();
  if (!code) {
    addMessageBubble("sentinel", `${connection.displayName} is connected. I can now use it as production signal context.`);
  }
  return connection;
}

async function syncIntegration(id) {
  const connection = await getJson(`/api/integration-connections/${id}/sync`, { method: "POST" });
  state.integrationConnections = state.integrationConnections.map((item) => item.id === connection.id ? connection : item);
  renderIntegrationConnections();
  await loadAll();
  addMessageBubble("sentinel", `${connection.displayName} sync completed. I updated the integration proof trail.`);
}

async function disconnectIntegration(id) {
  const connection = await getJson(`/api/integration-connections/${id}`, { method: "DELETE" });
  state.integrationConnections = state.integrationConnections.map((item) => item.id === connection.id ? connection : item);
  renderIntegrationConnections();
  await loadAll();
  addMessageBubble("sentinel", `${connection.displayName} is disconnected. I will stop treating it as live production context.`);
}

async function submitCommand(event) {
  event.preventDefault();
  const input = byId("commandInput");
  const command = input.value.trim();
  if (!command) return;
  addMessageBubble("user", command);
  input.value = "";
  markDemoStep("command");
  try {
    addMessageBubble("sentinel", await answerCommand(command));
  } catch (error) {
    addMessageBubble("sentinel", error.message || "I could not complete that analysis. Refresh the command center and try again.");
  }
}

async function runLiveDemo() {
  if (state.liveDemoRunning) return;
  state.liveDemoRunning = true;
  updateLiveDemoStatus("Starting live production scenario...", true);
  byId("briefing")?.scrollIntoView({
    behavior: sentinelScene.reducedMotion ? "auto" : "smooth",
    block: "start",
  });

  try {
    if (!state.auth?.token) {
      updateLiveDemoStatus("Opening secure demo workspace...", true);
      await login({ preventDefault() {} });
      if (!state.auth?.token) {
        throw new Error("Sign in did not complete. Check the demo credentials and try again.");
      }
      await demoPause();
    }

    markDemoStep("signedIn");
    addLiveDemoMessage("I am connecting GitHub, CI, and Jira so this demo uses real backend signal flow.");
    await installIntegration(null, "GITHUB", "demo-github-code", state.auth?.tenantId || "sentinel-demo");
    await installIntegration(null, "CI", "demo-ci-code", state.auth?.tenantId || "sentinel-demo");
    await installIntegration(null, "JIRA", "demo-jira-code", state.auth?.tenantId || "sentinel-demo");
    await loadAll();
    await demoPause();

    addLiveDemoMessage("Reviewing PR #418 before it reaches production.");
    const review = await getJson("/api/pr-reviews/simulate", {
      method: "POST",
      body: JSON.stringify({
        repository: "sentinel-ai/payment-api",
        prNumber: 418,
        title: "Refactor payment authorization and settlement retry path",
        author: "david",
        serviceName: "payment-api",
        ownerTeam: "Payments Platform",
        ciStatus: "failure",
        changedFiles: [
          "src/payments/AuthorizePayment.java",
          "src/payments/SettlementRetryPolicy.java",
          "db/migration/V43__payment_authorization_index.sql",
        ],
      }),
    });
    state.latestPrReview = review;
    state.prReviews = upsertById(state.prReviews, review);
    renderPrReviewResult();
    markDemoStep("prReview");
    addMessageBubble("sentinel", `AI Engineer reviewed PR #${review.prNumber}: ${review.recommendation} at ${review.riskScore}% risk.`);
    await demoPause();

    addMessageBubble("user", "Should I merge this pull request?");
    addMessageBubble("sentinel", await answerCommand("Should I merge this pull request?"));
    markDemoStep("command");
    await decidePrReview(review.id, "WAITING");
    await demoPause();

    addLiveDemoMessage("A production deployment signal just arrived from GitHub. I am projecting blast radius now.");
    let deployment = await getJson("/api/webhooks/github/simulate", {
      method: "POST",
      body: JSON.stringify({
        repository: "sentinel-ai/payment-api",
        serviceName: "payment-api",
        ownerTeam: "Payments Platform",
        environment: "production",
        commitSha: "abc1234",
        pullRequestTitle: "Update settlement retry and payment authorization migration",
        actor: state.auth?.username,
        ciStatus: "failure",
        changedFiles: [
          "src/payments/AuthorizePayment.java",
          "db/migration/V43__payment_authorization_index.sql",
          "src/payments/LedgerWriter.java",
        ],
        dependencies: ["checkout-service", "billing-service", "customer-ledger"],
      }),
    });
    state.deployments = upsertById(state.deployments, deployment);
    state.selectedId = deployment.id;
    await loadAll();
    await selectDeployment(deployment.id, false);
    await demoPause();

    addLiveDemoMessage("CI evidence is failing. Six tests are down and coverage dropped in a payment-critical path.");
    deployment = await getJson("/api/integrations/ci/simulate", {
      method: "POST",
      body: JSON.stringify({
        provider: "GitHub Actions",
        repository: "sentinel-ai/payment-api",
        serviceName: "payment-api",
        ownerTeam: "Payments Platform",
        environment: "production",
        commitSha: "abc1234",
        pipelineName: "payment-regression",
        status: "failure",
        failedTests: 6,
        coverageDelta: -14,
        actor: state.auth?.username,
        failedSuites: ["CheckoutRegression", "LedgerSettlementIT"],
        dependencies: ["checkout-service", "billing-service", "customer-ledger"],
      }),
    });
    state.selectedId = deployment.id;
    await loadAll();
    await selectDeployment(deployment.id, false);
    await demoPause();

    addLiveDemoMessage("Jira confirms customer-impacting work is tied to this release. I am updating incident command and memory.");
    deployment = await getJson("/api/integrations/jira/simulate", {
      method: "POST",
      body: JSON.stringify({
        issueKey: "PAY-912",
        summary: "Customer-impacting payment capture defect requires settlement retry changes",
        priority: "Critical",
        status: "In QA",
        issueType: "Incident",
        serviceName: "payment-api",
        ownerTeam: "Payments Platform",
        environment: "production",
        commitSha: "abc1234",
        actor: state.auth?.username,
        labels: ["hotfix", "customer-impact"],
        dependencies: ["checkout-service", "billing-service", "customer-ledger"],
      }),
    });
    state.selectedId = deployment.id;
    await loadAll();
    await selectDeployment(deployment.id, false);
    markDemoStep("memory");
    await demoPause();

    await refreshArchitectureBrain();
    await demoPause();

    const approvalNote = byId("approvalNote");
    if (approvalNote) {
      approvalNote.value = "Blocked by Sentinel live demo: failed CI, payment migration, customer-impacting Jira incident, and production blast radius.";
    }
    addLiveDemoMessage("Final decision: I am blocking the production release and writing an audit trail.");
    await decide(deployment.id, "BLOCK");
    updateLiveDemoStatus("Demo complete: Sentinel blocked the unsafe production release.", false);
  } catch (error) {
    const message = error.message || "The live demo could not finish.";
    addMessageBubble("sentinel", message);
    updateLiveDemoStatus(message, false);
  } finally {
    state.liveDemoRunning = false;
    byId("briefing")?.classList.remove("demo-running");
    byId("runLiveDemoButton").disabled = false;
    if (byId("runLiveDemoButton")) byId("runLiveDemoButton").textContent = "Run live incident demo";
  }
}

byId("loginForm").addEventListener("submit", login);
byId("cognitoLoginButton").addEventListener("click", startCognitoLogin);
byId("logoutButton").addEventListener("click", logout);
byId("refreshButton").addEventListener("click", loadAll);
byId("runLiveDemoButton").addEventListener("click", runLiveDemo);
byId("webhookForm").addEventListener("submit", simulateWebhook);
byId("ciSignalForm").addEventListener("submit", ingestCiSignal);
byId("jiraSignalForm").addEventListener("submit", ingestJiraSignal);
byId("prReviewForm").addEventListener("submit", simulatePrReview);
byId("architectureRefreshButton").addEventListener("click", refreshArchitectureBrain);
byId("integrationRefreshButton").addEventListener("click", refreshIntegrations);
byId("jobRefreshButton").addEventListener("click", refreshBackgroundJobs);
byId("webhookDeliveryRefreshButton").addEventListener("click", refreshWebhookDeliveries);
byId("commandForm").addEventListener("submit", submitCommand);
startSentinel3dScene();
renderDemoProgress();
document.querySelectorAll(".prompt-chip").forEach((button) => {
  button.addEventListener("click", () => {
    byId("commandInput").value = button.dataset.prompt;
    byId("commandForm").requestSubmit();
  });
});

async function initialize() {
  await loadAuthStatus();
  await handleCognitoCallback();
  await handleIntegrationCallback();
  await loadAll();
}

initialize();
