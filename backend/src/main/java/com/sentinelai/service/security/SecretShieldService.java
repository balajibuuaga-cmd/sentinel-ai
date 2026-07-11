package com.sentinelai.service.security;

import com.sentinelai.model.AuditEvent;
import com.sentinelai.model.security.SecretFinding;
import com.sentinelai.model.security.SecretFindingSource;
import com.sentinelai.model.security.SecretScanRequest;
import com.sentinelai.model.security.SecretScanResponse;
import com.sentinelai.model.security.SecretVerdict;
import com.sentinelai.repository.AuditEventRepository;
import com.sentinelai.security.TenantContext;
import com.sentinelai.service.ai.ChiefEngineerReasoningProvider;
import com.sentinelai.service.ai.SecretGateVerdict;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Sentinel Shield: a deterministic secret scanner gated by two AI judgments,
 * modeled on the scanner + risk/downgrade architecture from learned
 * secret-detection systems.
 *
 * - Scanner hits go to the DOWNGRADE gate with every candidate value masked;
 *   a hit is only cleared when the AI confidently calls it a false alarm.
 *   No AI available or an ambiguous answer means it stays blocked.
 * - Secret-looking lines the scanner missed go to the RISK gate with raw
 *   context; the fallback without AI is a pure entropy heuristic.
 *
 * Privacy: raw candidate values never leave this service - findings carry
 * masked snippets only, and the audit trail records counts, never content.
 */
@Service
public class SecretShieldService {

    private static final int WINDOW_RADIUS = 3;
    private static final int MAX_AI_GATE_CALLS = 12;
    private static final int MAX_FINDINGS = 100;
    // The scanner fires on keyword assignments at entropy >= 3.6, so risk candidates
    // are keyword lines whose value sits just below that. The no-AI fallback warns from
    // this lower band up to (but not including) the scanner threshold - above it, the
    // scanner already caught it as a hit.
    private static final double RISK_FALLBACK_ENTROPY = 3.0;
    private static final int SNIPPET_LIMIT = 160;

    private final SecretPatternScanner scanner;
    private final ChiefEngineerReasoningProvider reasoningProvider;
    private final AuditEventRepository auditEventRepository;
    private final TenantContext tenantContext;

    public SecretShieldService(
            SecretPatternScanner scanner,
            ChiefEngineerReasoningProvider reasoningProvider,
            AuditEventRepository auditEventRepository,
            TenantContext tenantContext
    ) {
        this.scanner = scanner;
        this.reasoningProvider = reasoningProvider;
        this.auditEventRepository = auditEventRepository;
        this.tenantContext = tenantContext;
    }

    public SecretScanResponse scan(SecretScanRequest request) {
        List<String> lines = request.content().lines().toList();
        String extension = extensionOf(request.filename());
        List<SecretPatternScanner.RawHit> hits = scanner.scan(lines);

        // Group hits per line so one downgrade judgment covers a line's hits,
        // and mask every hit across the whole file before building windows -
        // the downgrade gate must never see any candidate value.
        Map<Integer, List<SecretPatternScanner.RawHit>> hitsByLine = new TreeMap<>();
        hits.forEach(hit -> hitsByLine.computeIfAbsent(hit.lineIndex(), key -> new ArrayList<>()).add(hit));
        List<String> maskedLines = maskAll(lines, hits);

        List<SecretFinding> findings = new ArrayList<>();
        int aiCallsUsed = 0;
        boolean aiGateAvailable = false;

        for (Map.Entry<Integer, List<SecretPatternScanner.RawHit>> entry : hitsByLine.entrySet()) {
            if (findings.size() >= MAX_FINDINGS) {
                break;
            }
            int lineIndex = entry.getKey();
            String category = entry.getValue().stream()
                    .map(SecretPatternScanner.RawHit::category)
                    .distinct()
                    .sorted()
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Secret");
            SecretVerdict verdict = SecretVerdict.BLOCKED;
            String reason = "Deterministic scanner hit; no AI downgrade judgment was applied, so it stays blocked.";
            boolean aiJudged = false;

            if (aiCallsUsed < MAX_AI_GATE_CALLS) {
                aiCallsUsed++;
                Optional<SecretGateVerdict> gate = reasoningProvider.judgeMaskedScannerHit(
                        extension, window(maskedLines, lineIndex), focusIndex(lineIndex));
                if (gate.isPresent()) {
                    aiGateAvailable = true;
                    aiJudged = true;
                    verdict = gate.get().block() ? SecretVerdict.BLOCKED : SecretVerdict.CLEARED;
                    reason = gate.get().reason();
                }
            } else {
                reason = "Deterministic scanner hit; AI gate budget for this scan was reached, so it stays blocked.";
            }

            findings.add(new SecretFinding(
                    lineIndex + 1,
                    category,
                    truncate(maskedLines.get(lineIndex)),
                    SecretFindingSource.SCANNER,
                    verdict,
                    reason,
                    aiJudged
            ));
        }

        for (int i = 0; i < lines.size() && findings.size() < MAX_FINDINGS; i++) {
            if (hitsByLine.containsKey(i) || !scanner.isRiskCandidate(lines.get(i))) {
                continue;
            }
            SecretVerdict verdict = null;
            String reason;
            boolean aiJudged = false;

            if (aiCallsUsed < MAX_AI_GATE_CALLS) {
                aiCallsUsed++;
                Optional<SecretGateVerdict> gate = reasoningProvider.judgeRiskCandidate(
                        extension, window(lines, i), focusIndex(i));
                if (gate.isPresent()) {
                    aiGateAvailable = true;
                    aiJudged = true;
                    if (gate.get().block()) {
                        verdict = SecretVerdict.WARN;
                        reason = gate.get().reason();
                    } else {
                        continue; // AI cleared it - not a finding.
                    }
                } else {
                    reason = entropyFallbackReason(lines.get(i));
                    verdict = reason == null ? null : SecretVerdict.WARN;
                }
            } else {
                reason = entropyFallbackReason(lines.get(i));
                verdict = reason == null ? null : SecretVerdict.WARN;
            }

            if (verdict == null) {
                continue;
            }
            findings.add(new SecretFinding(
                    i + 1,
                    "Possible missed secret",
                    truncate(maskValueOnly(lines.get(i))),
                    SecretFindingSource.RISK_GATE,
                    verdict,
                    reason,
                    aiJudged
            ));
        }

        findings.sort(Comparator.comparingInt(SecretFinding::line));
        long blocked = findings.stream().filter(f -> f.verdict() == SecretVerdict.BLOCKED).count();
        long cleared = findings.stream().filter(f -> f.verdict() == SecretVerdict.CLEARED).count();
        long warned = findings.stream().filter(f -> f.verdict() == SecretVerdict.WARN).count();

        auditEventRepository.save(new AuditEvent(
                tenantContext.tenantId(),
                tenantContext.organizationName(),
                tenantContext.currentUsername() == null ? "sentinel-shield" : tenantContext.currentUsername(),
                "SECRET_SCAN",
                "file:" + (request.filename() == null || request.filename().isBlank() ? "<pasted content>" : request.filename()),
                "Scanned " + lines.size() + " lines: " + blocked + " blocked, " + cleared + " cleared, " + warned + " warned.",
                Instant.now()
        ));

        return new SecretScanResponse(
                lines.size(),
                (int) blocked,
                (int) cleared,
                (int) warned,
                blocked > 0,
                aiGateAvailable,
                findings
        );
    }

    private List<String> maskAll(List<String> lines, List<SecretPatternScanner.RawHit> hits) {
        List<StringBuilder> builders = new ArrayList<>(lines.size());
        lines.forEach(line -> builders.add(new StringBuilder(line)));
        for (SecretPatternScanner.RawHit hit : hits) {
            StringBuilder builder = builders.get(hit.lineIndex());
            for (int i = hit.start(); i < hit.end() && i < builder.length(); i++) {
                builder.setCharAt(i, '*');
            }
        }
        return builders.stream().map(StringBuilder::toString).toList();
    }

    /** Mask just the value part of a generic assignment so risk snippets are safe to return. */
    private String maskValueOnly(String line) {
        var matcher = java.util.regex.Pattern
                .compile("(?i)([:=]+\\s*[\"']?)([A-Za-z0-9+/=_\\-.!@#$%^&*]{8,})")
                .matcher(line);
        StringBuilder masked = new StringBuilder(line);
        while (matcher.find()) {
            for (int i = matcher.start(2); i < matcher.end(2); i++) {
                masked.setCharAt(i, '*');
            }
        }
        return masked.toString();
    }

    private String entropyFallbackReason(String line) {
        var matcher = java.util.regex.Pattern
                .compile("[:=]+\\s*[\"']?([A-Za-z0-9+/=_\\-.!@#$%^&*]{16,})")
                .matcher(line);
        while (matcher.find()) {
            double entropy = SecretPatternScanner.shannonEntropy(matcher.group(1));
            if (entropy >= RISK_FALLBACK_ENTROPY) {
                return "No AI judgment available; flagged by entropy heuristic ("
                        + String.format(java.util.Locale.ROOT, "%.2f", entropy) + " bits/char on the assigned value).";
            }
        }
        return null;
    }

    private List<String> window(List<String> lines, int center) {
        int from = Math.max(0, center - WINDOW_RADIUS);
        int to = Math.min(lines.size(), center + WINDOW_RADIUS + 1);
        return lines.subList(from, to);
    }

    private int focusIndex(int center) {
        return center - Math.max(0, center - WINDOW_RADIUS);
    }

    private String truncate(String line) {
        return line.length() <= SNIPPET_LIMIT ? line : line.substring(0, SNIPPET_LIMIT) + "…";
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".") || filename.endsWith(".")) {
            return "<none>";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
