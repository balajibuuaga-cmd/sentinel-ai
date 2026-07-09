package com.sentinelai.model;

public enum WebhookDeliveryStatus {
    RECEIVED,
    SUCCEEDED,
    FAILED,
    REPLAY_QUEUED,
    REPLAYED,
    EXPIRED
}
