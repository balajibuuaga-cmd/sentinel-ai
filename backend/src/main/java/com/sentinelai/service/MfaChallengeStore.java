package com.sentinelai.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use, in-memory store for MFA login challenges. Deliberately
 * NOT a JWT: an opaque random token here can never be mistaken for a real access
 * token by JwtAuthenticationFilter, so a leaked challenge token grants nothing
 * beyond "may attempt one MFA code for this username."
 */
@Service
public class MfaChallengeStore {

    private static final Duration CHALLENGE_VALIDITY = Duration.ofMinutes(5);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private record Challenge(String username, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public String issueChallenge(String username) {
        byte[] tokenBytes = new byte[24];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        challenges.put(token, new Challenge(username, Instant.now().plus(CHALLENGE_VALIDITY)));
        return token;
    }

    public Optional<String> resolveAndConsume(String token) {
        Challenge challenge = challenges.remove(token);
        if (challenge == null || challenge.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(challenge.username());
    }
}
