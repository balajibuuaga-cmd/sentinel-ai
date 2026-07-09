package com.sentinelai.security;

public record DemoUser(String username, String password, String role, String tenantId, String organizationName) {
}
