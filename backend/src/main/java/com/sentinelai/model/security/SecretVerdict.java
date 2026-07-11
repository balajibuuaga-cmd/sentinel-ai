package com.sentinelai.model.security;

public enum SecretVerdict {
    /** Scanner hit that stays blocked (AI agreed, AI unavailable, or AI unsure). */
    BLOCKED,
    /** Scanner hit the downgrade gate confidently cleared as a false alarm. */
    CLEARED,
    /** No scanner hit, but the risk gate judged the line likely secret-bearing. */
    WARN
}
