import { useMemo, useState } from 'react';
import MetricCards from '../components/MetricCards';
import EngineeringUniverse from '../components/EngineeringUniverse';
import { ExecutiveBriefing, AICopilot } from '../components/AIPanels';
import ActivityFeed from '../components/ActivityFeed';
import { RiskHeatmapCard, RiskTrendCard, FutureTrendCard, BusinessImpactCard } from '../components/BottomCharts';
import IncidentReplayCard from '../components/IncidentReplayCard';
import ActionBar from '../components/ActionBar';
import { buildMetricCards, buildRiskTrend, buildRiskProjection } from '../api/transform';
import type { DashboardData } from '../hooks/useDashboard';

interface Props {
  data: DashboardData;
  refresh: () => void;
}

type Timeframe = 'today' | 'yesterday';

export default function CommandCenter({ data, refresh }: Props) {
  const [timeframe, setTimeframe] = useState<Timeframe>('today');

  const referenceDate = useMemo(() => {
    const date = new Date();
    if (timeframe === 'yesterday') date.setDate(date.getDate() - 1);
    return date;
  }, [timeframe]);

  const metrics = useMemo(
    () => (timeframe === 'today' ? data.metrics : buildMetricCards(data.deployments, data.incidents, referenceDate)),
    [timeframe, data.metrics, data.deployments, data.incidents, referenceDate],
  );

  const riskTrend = useMemo(
    () => (timeframe === 'today' ? data.riskTrend : buildRiskTrend(data.deployments, referenceDate)),
    [timeframe, data.riskTrend, data.deployments, referenceDate],
  );

  const riskProjection = useMemo(
    () => buildRiskProjection(data.deployments, referenceDate),
    [data.deployments, referenceDate],
  );

  const serviceRiskScores = useMemo(() => {
    const scores: Record<string, number> = {};
    data.deployments.forEach((deployment) => {
      if (!deployment.riskAssessment) return;
      const existing = scores[deployment.serviceName];
      if (existing === undefined || deployment.riskAssessment.score > existing) {
        scores[deployment.serviceName] = deployment.riskAssessment.score;
      }
    });
    return scores;
  }, [data.deployments]);

  return (
    <>
      <div className="time-machine">
        <span className="time-machine-label">Time Machine</span>
        <div className="time-machine-toggle">
          <button
            className={timeframe === 'yesterday' ? 'active' : ''}
            onClick={() => setTimeframe('yesterday')}
          >
            Yesterday
          </button>
          <button className={timeframe === 'today' ? 'active' : ''} onClick={() => setTimeframe('today')}>
            Today
          </button>
        </div>
      </div>

      <div className="content-grid">
        <div className="main-col">
          <MetricCards metrics={metrics} />
          <EngineeringUniverse
            nodes={data.serviceGraph.nodes}
            edges={data.serviceGraph.edges}
            riskScores={serviceRiskScores}
            onRefresh={refresh}
          />
          <div className="bottom-charts-row">
            <RiskHeatmapCard data={data.riskHeatmap} />
            <RiskTrendCard data={riskTrend} />
            <FutureTrendCard data={riskProjection} />
            <BusinessImpactCard data={data.businessImpact} />
          </div>
          <IncidentReplayCard incidents={data.incidents} serviceGraph={data.serviceGraph} />
        </div>

        <div className="side-col">
          <ExecutiveBriefing briefing={data.executiveBriefing} />
          <AICopilot />
          <ActivityFeed items={data.activityFeed} />
        </div>
      </div>

      <ActionBar />
    </>
  );
}
