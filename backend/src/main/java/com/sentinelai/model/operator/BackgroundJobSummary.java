package com.sentinelai.model.operator;

public record BackgroundJobSummary(
        long queued,
        long running,
        long failed,
        long succeeded
) {
}
