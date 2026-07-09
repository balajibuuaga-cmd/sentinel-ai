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
    private final boolean redisEnabled;
    private volatile String lastBackend;

    public RateLimitService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${sentinel.security.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${sentinel.security.rate-limit.redis-enabled:false}") boolean redisEnabled
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.requestsPerMinute = requestsPerMinute;
        this.redisEnabled = redisEnabled;
        this.lastBackend = redisEnabled ? "redis-not-used-yet" : "memory";
    }

    public RateLimitDecision check(String clientKey) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (redisEnabled) {
            try {
                StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
                if (redisTemplate != null) {
                    String key = "sentinel:rate-limit:" + minute + ":" + clientKey;
                    Long current = redisTemplate.opsForValue().increment(key);
                    redisTemplate.expire(key, Duration.ofSeconds(75));
                    lastBackend = "redis";
                    return new RateLimitDecision(requestsPerMinute, current == null ? 1 : current.intValue(), lastBackend);
                }
            } catch (RuntimeException ex) {
                lastBackend = "memory-fallback";
                return inMemory(clientKey, minute, lastBackend);
            }
        }
        lastBackend = "memory";
        return inMemory(clientKey, minute, lastBackend);
    }

    public int limit() {
        return requestsPerMinute;
    }

    public boolean redisEnabled() {
        return redisEnabled;
    }

    public String backend() {
        return lastBackend;
    }

    private RateLimitDecision inMemory(String clientKey, long minute, String backend) {
        Window window = windows.compute(clientKey, (ignored, existing) ->
                existing == null || existing.minute != minute ? new Window(minute) : existing
        );
        return new RateLimitDecision(requestsPerMinute, window.count.incrementAndGet(), backend);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
