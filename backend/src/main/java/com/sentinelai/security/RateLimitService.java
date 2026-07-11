package com.sentinelai.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final int requestsPerMinute;
    private final int authRequestsPerMinute;
    private final boolean redisEnabled;
    private volatile String lastBackend;

    public RateLimitService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${sentinel.security.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${sentinel.security.rate-limit.auth-requests-per-minute:15}") int authRequestsPerMinute,
            @Value("${sentinel.security.rate-limit.redis-enabled:false}") boolean redisEnabled
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.requestsPerMinute = requestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
        this.redisEnabled = redisEnabled;
        this.lastBackend = redisEnabled ? "redis-not-used-yet" : "memory";
    }

    /** General per-IP limit applied to all API traffic. */
    public RateLimitDecision check(String clientKey) {
        return check(clientKey, requestsPerMinute, "general");
    }

    /**
     * Tighter limit for abusable auth endpoints (login, signup, password reset,
     * MFA verify). Counted in its own bucket so normal API traffic can never
     * exhaust an attacker's brute-force budget or vice versa.
     */
    public RateLimitDecision checkSensitive(String clientKey) {
        return check(clientKey, authRequestsPerMinute, "auth");
    }

    private RateLimitDecision check(String clientKey, int limit, String bucket) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (redisEnabled) {
            try {
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate != null) {
                    String key = "sentinel:rate-limit:" + bucket + ":" + minute + ":" + clientKey;
                    Long current = redisTemplate.opsForValue().increment(key);
                    redisTemplate.expire(key, Duration.ofSeconds(75));
                    lastBackend = "redis";
                    return new RateLimitDecision(limit, current == null ? 1 : current.intValue(), lastBackend);
                }
            } catch (RuntimeException ex) {
                lastBackend = "memory-fallback";
                return inMemory(bucket + ":" + clientKey, minute, limit, lastBackend);
            }
        }
        lastBackend = "memory";
        return inMemory(bucket + ":" + clientKey, minute, limit, lastBackend);
    }

    public int limit() {
        return requestsPerMinute;
    }

    public int authLimit() {
        return authRequestsPerMinute;
    }

    public boolean redisEnabled() {
        return redisEnabled;
    }

    public String backend() {
        return lastBackend;
    }

    private RateLimitDecision inMemory(String windowKey, long minute, int limit, String backend) {
        Window window = windows.compute(windowKey, (ignored, existing) ->
                existing == null || existing.minute != minute ? new Window(minute) : existing
        );
        return new RateLimitDecision(limit, window.count.incrementAndGet(), backend);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
