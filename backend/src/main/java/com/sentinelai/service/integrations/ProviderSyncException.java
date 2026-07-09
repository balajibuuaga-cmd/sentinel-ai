package com.sentinelai.service.integrations;

public class ProviderSyncException extends RuntimeException {

    private final ProviderSyncFailureCategory category;

    public ProviderSyncException(ProviderSyncFailureCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ProviderSyncFailureCategory category() {
        return category;
    }
}
