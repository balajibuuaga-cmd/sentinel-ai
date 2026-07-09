package com.sentinelai.model.intelligence;

import java.util.List;

public record ExecutiveBriefing(
        String greeting,
        String summary,
        String recommendationTitle,
        String recommendation,
        String chiefBriefing,
        List<MetricInsight> metrics
) {
}
