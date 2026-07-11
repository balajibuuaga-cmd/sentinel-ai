package com.sentinelai.model.security;

public enum SecretFindingSource {
    /** The deterministic pattern/entropy scanner fired on this line. */
    SCANNER,
    /** The scanner did not fire, but the line looked secret-bearing and went to the risk gate. */
    RISK_GATE
}
