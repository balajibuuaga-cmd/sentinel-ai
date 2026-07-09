package com.sentinelai.security;

public record LoginResult(AuthResponse authResponse, boolean mfaRequired, String mfaChallengeToken) {

    public static LoginResult success(AuthResponse response) {
        return new LoginResult(response, false, null);
    }

    public static LoginResult mfaRequired(String challengeToken) {
        return new LoginResult(null, true, challengeToken);
    }
}
