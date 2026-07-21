package com.sentinelai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sentinelai.model.GitHubWebhookRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Turns GitHub's own webhook payloads into {@link GitHubWebhookRequest}.
 *
 * <p>The endpoint verified signatures correctly but then deserialised the body
 * straight into {@code GitHubWebhookRequest}, whose fields GitHub does not send:
 * its {@code repository} is an object rather than a string, and there is no
 * {@code serviceName}, {@code ownerTeam} or {@code commitSha} anywhere in the
 * payload. Every delivery from a real repository would have failed, so the
 * feature only ever worked for a caller hand-rolling Sentinel's own shape.
 */
@Component
public class GitHubEventTranslator {

    /**
     * @return the deployment signal this event represents, or empty when the
     *         event carries none (a ping, or an event type Sentinel ignores).
     *         Empty means accept and record, not reject: answering an ignored
     *         event with an error would show up as a failed delivery in GitHub.
     */
    public Optional<GitHubWebhookRequest> translate(String eventType, JsonNode root) {
        if (eventType == null) {
            return Optional.empty();
        }
        return switch (eventType) {
            case "pull_request" -> pullRequest(root);
            case "push" -> push(root);
            default -> Optional.empty();
        };
    }

    private Optional<GitHubWebhookRequest> pullRequest(JsonNode root) {
        String action = root.path("action").asText("");
        // Only states where the code actually changed are worth re-scoring.
        if (!List.of("opened", "reopened", "synchronize", "ready_for_review").contains(action)) {
            return Optional.empty();
        }
        JsonNode pull = root.path("pull_request");
        String repository = root.path("repository").path("full_name").asText("");
        if (repository.isBlank() || pull.isMissingNode()) {
            return Optional.empty();
        }
        return Optional.of(new GitHubWebhookRequest(
                repository,
                serviceName(repository),
                ownerTeam(root, repository),
                // A pull request is a candidate for production, not production.
                "review",
                firstNonBlank(pull.path("head").path("sha").asText(""), "unknown-sha"),
                firstNonBlank(pull.path("title").asText(""), "Pull request"),
                firstNonBlank(root.path("sender").path("login").asText(""), "github"),
                null,
                List.of(),
                List.of()
        ));
    }

    private Optional<GitHubWebhookRequest> push(JsonNode root) {
        String repository = root.path("repository").path("full_name").asText("");
        if (repository.isBlank()) {
            return Optional.empty();
        }
        String ref = root.path("ref").asText("");
        String defaultBranch = root.path("repository").path("default_branch").asText("main");
        JsonNode head = root.path("head_commit");
        // A push that deleted a branch carries no commit to assess.
        if (head.isMissingNode() || head.isNull()) {
            return Optional.empty();
        }
        boolean toDefaultBranch = ref.endsWith("/" + defaultBranch);
        return Optional.of(new GitHubWebhookRequest(
                repository,
                serviceName(repository),
                ownerTeam(root, repository),
                toDefaultBranch ? "production" : "review",
                firstNonBlank(head.path("id").asText(""), "unknown-sha"),
                firstNonBlank(firstLine(head.path("message").asText("")), "Push"),
                firstNonBlank(root.path("pusher").path("name").asText(""), "github"),
                null,
                changedFiles(head),
                List.of()
        ));
    }

    private List<String> changedFiles(JsonNode head) {
        return java.util.stream.Stream.of("added", "modified", "removed")
                .flatMap(field -> java.util.stream.StreamSupport.stream(head.path(field).spliterator(), false))
                .map(JsonNode::asText)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String serviceName(String repository) {
        int slash = repository.indexOf('/');
        return slash >= 0 && slash < repository.length() - 1 ? repository.substring(slash + 1) : repository;
    }

    private String ownerTeam(JsonNode root, String repository) {
        String login = root.path("repository").path("owner").path("login").asText("");
        if (!login.isBlank()) {
            return login;
        }
        int slash = repository.indexOf('/');
        return slash > 0 ? repository.substring(0, slash) : repository;
    }

    private String firstLine(String value) {
        int newline = value.indexOf('\n');
        return newline >= 0 ? value.substring(0, newline) : value;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
