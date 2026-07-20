import { useCallback, useEffect, useState } from 'react';
import { api, ApiError, clearSession, currentSession, hasStoredSession } from '../api/client';
import {
  buildActivityFeed,
  buildBusinessImpact,
  buildEngineeringHealth,
  buildExecutiveBriefing,
  buildHeaderStats,
  buildMetricCards,
  buildRiskHeatmap,
  buildRiskTrend,
  buildServiceGraph,
  buildUnifiedRisks,
} from '../api/transform';
import type {
  ActivityItemData,
  BusinessImpactData,
  EngineeringHealthData,
  HeaderStatsData,
  MetricCardData,
  RiskHeatmapSlice,
  RiskTrendPoint,
  ServiceEdge,
  ServiceNode,
} from '../types/dashboard';
import type { Deployment, ExecutiveBriefing, Incident } from '../api/types';

const POLL_INTERVAL_MS = 20000;

export interface DashboardData {
  header: HeaderStatsData;
  metrics: MetricCardData[];
  serviceGraph: { nodes: ServiceNode[]; edges: ServiceEdge[] };
  riskHeatmap: RiskHeatmapSlice[];
  riskTrend: RiskTrendPoint[];
  businessImpact: BusinessImpactData;
  executiveBriefing: ExecutiveBriefing;
  engineeringHealth: EngineeringHealthData;
  activityFeed: ActivityItemData[];
  incidentCount: number;
  riskCount: number;
  deployments: Deployment[];
  incidents: Incident[];
}

export function useDashboard() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [needsLogin, setNeedsLogin] = useState(!hasStoredSession());

  const load = useCallback(async () => {
    if (!hasStoredSession()) {
      setNeedsLogin(true);
      setLoading(false);
      return;
    }

    const session = currentSession();
    if (!session) {
      clearSession();
      setNeedsLogin(true);
      setLoading(false);
      return;
    }

    try {
      const [operator, briefing, dna, deployments, incidents, brain, auditEvents] = await Promise.all([
        // The operator console is ADMIN/RELEASE_MANAGER only. A VIEWER gets 403
        // here, which must degrade that one panel rather than fail the whole
        // dashboard — any other failure still propagates.
        api.operatorConsole().catch((err) => {
          if (err instanceof ApiError && err.status === 403) {
            return null;
          }
          throw err;
        }),
        api.executiveBriefing(),
        api.engineeringDna(),
        api.deployments(),
        api.incidents(),
        api.architectureBrain(),
        api.auditEvents(),
      ]);

      setData({
        header: buildHeaderStats(session),
        metrics: buildMetricCards(deployments, incidents),
        serviceGraph: buildServiceGraph(brain),
        riskHeatmap: buildRiskHeatmap(brain),
        riskTrend: buildRiskTrend(deployments),
        businessImpact: buildBusinessImpact(operator, deployments, incidents),
        executiveBriefing: buildExecutiveBriefing(briefing),
        engineeringHealth: buildEngineeringHealth(dna),
        activityFeed: buildActivityFeed(auditEvents),
        incidentCount: incidents.length,
        // Same merge the Risks page renders, so the sidebar badge and that page
        // can never show different totals.
        riskCount: buildUnifiedRisks(brain, deployments).length,
        deployments,
        incidents,
      });
      setError(null);
      setNeedsLogin(false);
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        // 401 means the stored session is missing or expired: drop it and send
        // the user to login rather than showing a "backend unreachable" error.
        clearSession();
        setNeedsLogin(true);
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load dashboard data');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const interval = setInterval(load, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [load]);

  return { data, error, loading, needsLogin, refresh: load };
}
