package com.sentinelai.model.security;

/**
 * One judged finding from a secret scan. The snippet is always masked before it
 * leaves the scan service - raw candidate values are never returned, logged, or
 * persisted anywhere.
 */
public record SecretFinding(
        int line,
        String category,
        String maskedSnippet,
        SecretFindingSource source,
        SecretVerdict verdict,
        String reason,
        boolean aiJudged
) {
}
