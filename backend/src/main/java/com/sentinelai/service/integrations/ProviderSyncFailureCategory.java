package com.sentinelai.service.integrations;

public enum ProviderSyncFailureCategory {
    AUTH_EXPIRED,
    MISSING_SCOPE,
    RATE_LIMITED,
    PROVIDER_DOWN,
    BAD_CONFIG,
    UNKNOWN
}
