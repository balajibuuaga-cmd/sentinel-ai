package com.sentinelai.security;

public record AuthResponse(String token, String username, String role, String tenantId, String organizationName) {
}
