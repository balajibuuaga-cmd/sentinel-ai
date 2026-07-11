package com.sentinelai.model.security;

import java.util.List;

public record SecretScanResponse(
        int linesScanned,
        int blockedCount,
        int clearedCount,
        int warnedCount,
        boolean wouldBlockCommit,
        boolean aiGateAvailable,
        List<SecretFinding> findings
) {
}
