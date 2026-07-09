package com.sentinelai.service;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.RiskAssessment;
import com.sentinelai.model.RiskLevel;
import com.sentinelai.model.RiskReason;
import com.sentinelai.model.Signal;
import com.sentinelai.model.SignalType;
import com.sentinelai.service.ai.ChiefEngineerReasoningProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RiskAnalysisService {

    private final ChiefEngineerReasoningProvider reasoningProvider;

    public RiskAnalysisService(ChiefEngineerReasoningProvider reasoningProvider) {
        this.reasoningProvider = reasoningProvider;
    }

    public RiskAssessment analyze(Deployment deployment) {
        List<RiskReason> reasons = new ArrayList<>();
        int score = "production".equalsIgnoreCase(deployment.getEnvironment()) ? 20 : 8;

        for (Signal signal : deployment.getSignals()) {
            score += signal.riskWeight();
            reasons.add(toReason(signal));
        }

        if (deployment.getDependencies().size() >= 3) {
            score += 12;
            reasons.add(new RiskReason(
                    "Dependency blast radius",
                    deployment.getServiceName() + " has " + deployment.getDependencies().size()
                            + " downstream dependencies.",
                    12
            ));
        }

        score = Math.min(score, 100);
        RiskLevel level = toLevel(score);
        reasons.sort(Comparator.comparingInt(RiskReason::impact).reversed());

        return new RiskAssessment(
                score,
                level,
                reasoningProvider.deploymentRecommendation(score),
                reasoningProvider.deploymentExplanation(deployment, score, level, reasons),
                reasons,
                Instant.now()
        );
    }

    private RiskReason toReason(Signal signal) {
        String category = switch (signal.type()) {
            case GITHUB -> "Code change";
            case JIRA -> "Active work context";
            case CI -> "Build and test signal";
            case LOGS -> "Runtime behavior";
            case INCIDENT_HISTORY -> "Incident memory";
            case SERVICE_DEPENDENCY -> "Service dependency";
        };

        return new RiskReason(category, signal.title() + ": " + signal.description(), signal.riskWeight());
    }

    private RiskLevel toLevel(int score) {
        if (score >= 85) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 65) {
            return RiskLevel.HIGH;
        }
        if (score >= 35) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

}
