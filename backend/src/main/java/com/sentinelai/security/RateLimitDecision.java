package com.sentinelai.security;

public record RateLimitDecision(
        int limit,
        int current,
        String backend
) {
    public int remaining() {
        return Math.max(0, limit - current);
    }

    public boolean exceeded() {
        return current > limit;
    }
}
