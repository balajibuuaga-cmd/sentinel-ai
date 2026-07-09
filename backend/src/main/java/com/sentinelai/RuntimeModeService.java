package com.sentinelai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RuntimeModeService {

    private final boolean apiEnabled;
    private final boolean workerEnabled;

    public RuntimeModeService(
            @Value("${sentinel.api.enabled:true}") boolean apiEnabled,
            @Value("${sentinel.worker.enabled:true}") boolean workerEnabled
    ) {
        this.apiEnabled = apiEnabled;
        this.workerEnabled = workerEnabled;
    }

    public boolean apiEnabled() {
        return apiEnabled;
    }

    public boolean workerEnabled() {
        return workerEnabled;
    }

    public String mode() {
        if (apiEnabled && workerEnabled) {
            return "combined";
        }
        if (apiEnabled) {
            return "api";
        }
        if (workerEnabled) {
            return "worker";
        }
        return "disabled";
    }
}
