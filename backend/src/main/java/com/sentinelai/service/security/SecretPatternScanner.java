package com.sentinelai.service.security;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic first line of defense: fixed provider-token patterns plus a
 * Shannon-entropy detector for generic credential assignments. Mirrors the
 * classic scanner design - fast, explainable, and tunable - with the known
 * failure modes (placeholder false positives, novel-format false negatives)
 * handled by the AI gates layered on either side of it.
 */
@Service
public class SecretPatternScanner {

    public record RawHit(int lineIndex, String category, int start, int end) {
    }

    private record NamedPattern(String category, Pattern pattern, int valueGroup) {
    }

    private static final List<NamedPattern> PATTERNS = List.of(
            new NamedPattern("AWS access key ID", Pattern.compile("\\b(?:AKIA|ASIA|ABIA|ACCA)[0-9A-Z]{16}\\b"), 0),
            new NamedPattern("AWS secret access key", Pattern.compile("(?i)aws[^\\n]{0,20}['\"]([0-9a-zA-Z/+]{40})['\"]"), 1),
            new NamedPattern("GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36,}\\b"), 0),
            new NamedPattern("Slack token", Pattern.compile("\\bxox[baprs]-[A-Za-z0-9-]{10,}\\b"), 0),
            new NamedPattern("Stripe live key", Pattern.compile("\\b[sr]k_live_[A-Za-z0-9]{16,}\\b"), 0),
            new NamedPattern("Google API key", Pattern.compile("\\bAIza[0-9A-Za-z_\\-]{35}\\b"), 0),
            new NamedPattern("Private key material", Pattern.compile("-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY"), 0),
            new NamedPattern("JWT", Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}\\b"), 0),
            new NamedPattern("Connection string with credentials", Pattern.compile("\\b(?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqp)://[^:/\\s]+:([^@\\s]+)@"), 1)
    );

    private static final Pattern GENERIC_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|client[_-]?secret|private[_-]?key|credential)s?\\b\\s*[:=]+\\s*[\"']?([A-Za-z0-9+/=_\\-.!@#$%^&*]{8,})[\"']?"
    );

    private static final double GENERIC_ENTROPY_THRESHOLD = 3.6;

    public List<RawHit> scan(List<String> lines) {
        List<RawHit> hits = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (NamedPattern named : PATTERNS) {
                Matcher matcher = named.pattern().matcher(line);
                while (matcher.find()) {
                    hits.add(new RawHit(i, named.category(), matcher.start(named.valueGroup()), matcher.end(named.valueGroup())));
                }
            }
            Matcher generic = GENERIC_ASSIGNMENT.matcher(line);
            while (generic.find()) {
                String value = generic.group(2);
                if (shannonEntropy(value) >= GENERIC_ENTROPY_THRESHOLD && !overlapsExisting(hits, i, generic.start(2), generic.end(2))) {
                    hits.add(new RawHit(i, "High-entropy credential assignment", generic.start(2), generic.end(2)));
                }
            }
        }
        return hits;
    }

    /**
     * Lines the scanner did not fire on but that still look secret-bearing:
     * a credential-ish keyword next to an assigned value. These are the risk
     * gate's candidates.
     */
    public boolean isRiskCandidate(String line) {
        return GENERIC_ASSIGNMENT.matcher(line).find();
    }

    public static double shannonEntropy(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        Map<Character, Integer> counts = new HashMap<>();
        for (char c : value.toCharArray()) {
            counts.merge(c, 1, Integer::sum);
        }
        double entropy = 0;
        for (int count : counts.values()) {
            double p = (double) count / value.length();
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private boolean overlapsExisting(List<RawHit> hits, int lineIndex, int start, int end) {
        return hits.stream().anyMatch(hit ->
                hit.lineIndex() == lineIndex && hit.start() < end && hit.end() > start);
    }
}
