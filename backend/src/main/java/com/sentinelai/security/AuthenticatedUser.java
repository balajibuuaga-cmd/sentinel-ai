package com.sentinelai.security;

public record AuthenticatedUser(String username, String role, String tenantId, String organizationName) {
}
