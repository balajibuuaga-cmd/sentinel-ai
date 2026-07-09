package com.sentinelai.service.ai;

import com.sentinelai.model.Deployment;
import com.sentinelai.model.PullRequestRecommendation;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.model.RiskLevel;
import com.sentinelai.model.RiskReason;
import com.sentinelai.service.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.List;

/**
 * Calls Claude directly through AWS's own Bedrock Runtime SDK (Converse API), using the
 * account's AWS credentials/billing rather than a separate Anthropic Console API key.
 * Anthropic's own Bedrock client ("Mantle") does not currently support the cross-region
 * inference-profile ids ("us.anthropic....") that this account's models require for
 * on-demand invocation, so this class talks to Bedrock natively instead - confirmed working
 * via `aws bedrock-runtime converse` before wiring it here.
 */
@Service
@ConditionalOnProperty(name = "sentinel.ai.provider", havingValue = "anthropic")
public class AnthropicChiefEngineerReasoningProvider implements ChiefEngineerReasoningProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChiefEngineerReasoningProvider.class);

    // application.properties resolves sentinel.ai.model to this literal whenever SENTINEL_AI_MODEL
    // is unset, since that property is shared with the deterministic provider's status reporting.
    // Treat it as "not configured for a real model" rather than sending it to Bedrock as a model id.
    private static final String UNCONFIGURED_MODEL_PLACEHOLDER = "deterministic-chief-engineer-v1";
    // Cross-region inference profile id - confirmed working on this account via
    // `aws bedrock-runtime converse`. Opus 4.8 and Sonnet 5 returned AccessDeniedException on
    // this account (needs an AWS Sales access request); Sonnet 4.6 is the newest model it can
    // actually invoke today.
    private static final String DEFAULT_MODEL = "us.anthropic.claude-sonnet-4-6";
    private static final Region AWS_REGION = Region.US_EAST_1;

    private final BedrockRuntimeClient client;
    private final String model;
    private final AiUsageService aiUsageService;
    private final DeterministicChiefEngineerReasoningProvider fallback = new DeterministicChiefEngineerReasoningProvider();

    public AnthropicChiefEngineerReasoningProvider(
            @Value("${sentinel.ai.model:}") String configuredModel,
            AiUsageService aiUsageService
    ) {
        this.client = BedrockRuntimeClient.builder()
                .region(AWS_REGION)
                .build();
        this.model = (configuredModel == null || configuredModel.isBlank() || configuredModel.equals(UNCONFIGURED_MODEL_PLACEHOLDER))
                ? DEFAULT_MODEL
                : configuredModel;
        this.aiUsageService = aiUsageService;
    }

    @Override
    public String name() {
        return "anthropic-chief-engineer";
    }

    public String effectiveModel() {
        return model;
    }

    @Override
    public String deploymentRecommendation(int score) {
        // A fixed business rule tied to the documented release decision bands (docs/PRD.md) —
        // keep this deterministic so approval workflow thresholds never drift between requests.
        return fallback.deploymentRecommendation(score);
    }

    @Override
    public String deploymentExplanation(Deployment deployment, int score, RiskLevel level, List<RiskReason> reasons) {
        try {
            String user = "Deployment " + deployment.getDeploymentKey() + " (" + deployment.getServiceName()
                    + ", owned by " + deployment.getOwnerTeam() + ", environment " + deployment.getEnvironment()
                    + ") was assessed at " + score + "% risk, level " + level + ".\n"
                    + "Dependencies: " + String.join(", ", deployment.getDependencies()) + "\n"
                    + "Evidence:\n" + formatReasons(reasons);
            return callClaude(
                    "deployment_explanation",
                    "You are Sentinel AI, an AI Chief Engineer that explains deployment release risk to engineers in 2-3 sentences of plain English. "
                            + "Be concrete and cite the strongest evidence. Do not restate the raw score or level verbatim; explain what it means. "
                            + "Reply in plain prose only, no markdown formatting (no asterisks, bullet points, or headers).",
                    user);
        } catch (Exception e) {
            log.warn("Bedrock call failed for deploymentExplanation, falling back to deterministic reasoning", e);
            return fallback.deploymentExplanation(deployment, score, level, reasons);
        }
    }

    @Override
    public String pullRequestExplanation(
            PullRequestReviewRequest request,
            Deployment linkedDeployment,
            int score,
            PullRequestRecommendation recommendation
    ) {
        try {
            String linkedInfo = linkedDeployment == null
                    ? "No active deployment review is linked to this service."
                    : "Linked deployment " + linkedDeployment.getDeploymentKey() + " has "
                    + linkedDeployment.getDependencies().size() + " downstream dependencies.";
            String user = "PR #" + request.prNumber() + " in " + request.repository() + " titled \"" + request.title()
                    + "\" by " + request.author() + " changes " + request.changedFiles().size() + " files. "
                    + "CI status: " + request.ciStatus() + ". Risk score: " + score + "%. Recommendation: " + recommendation + ".\n"
                    + linkedInfo;
            return callClaude(
                    "pull_request_review",
                    "You are Sentinel AI, an AI Engineer that explains pull request merge/wait/block recommendations to engineers in 2-3 sentences. "
                            + "Justify the recommendation using the specific evidence given. "
                            + "Reply in plain prose only, no markdown formatting (no asterisks, bullet points, or headers).",
                    user);
        } catch (Exception e) {
            log.warn("Bedrock call failed for pullRequestExplanation, falling back to deterministic reasoning", e);
            return fallback.pullRequestExplanation(request, linkedDeployment, score, recommendation);
        }
    }

    @Override
    public String executiveChiefBriefing(
            String organizationName,
            int deploymentCount,
            int dependencyCount,
            int auditEventCount,
            Deployment riskiest,
            RiskReason strongestReason
    ) {
        try {
            var assessment = riskiest.getRiskAssessment();
            String user = "Organization: " + organizationName + ". Reviewed " + deploymentCount + " deployments, "
                    + dependencyCount + " service dependencies, " + auditEventCount + " audit events.\n"
                    + "Riskiest release: " + riskiest.getServiceName() + " (" + riskiest.getOwnerTeam() + ") at "
                    + assessment.score() + "% " + assessment.level() + " risk.\n"
                    + "Strongest evidence: " + strongestReason.evidence() + "\n"
                    + "Recommendation: " + assessment.recommendation();
            return callClaude(
                    "executive_briefing",
                    "You are Sentinel AI, an AI Chief Engineer writing a short executive briefing paragraph (3-4 sentences) "
                            + "summarizing today's release risk posture for a VP of Engineering. Be direct and specific. "
                            + "Reply in plain prose only, no markdown formatting (no asterisks, bullet points, or headers).",
                    user);
        } catch (Exception e) {
            log.warn("Bedrock call failed for executiveChiefBriefing, falling back to deterministic reasoning", e);
            return fallback.executiveChiefBriefing(organizationName, deploymentCount, dependencyCount, auditEventCount, riskiest, strongestReason);
        }
    }

    @Override
    public String deploymentQuestionAnswer(DeploymentQuestionContext context) {
        try {
            var assessment = context.assessment();
            var deployment = context.deployment();
            String user = "Question: \"" + context.normalizedQuestion() + "\"\n"
                    + "Deployment under discussion: " + deployment.getServiceName() + " at " + assessment.score()
                    + "% " + assessment.level() + " risk.\n"
                    + "Evidence:\n" + formatReasons(assessment.reasons())
                    + "Risky releases needing attention: " + context.riskyReleaseCount() + "\n"
                    + "Executive briefing on file: " + context.executiveBriefing() + "\n"
                    + "Memory notes: " + context.memoryAnswer() + "\n"
                    + "Engineering DNA notes: " + context.engineeringDnaAnswer();
            return callClaude(
                    "copilot_question",
                    "You are Sentinel AI, an AI Chief Engineer answering a release manager's question directly in 2-4 sentences, "
                            + "grounded only in the evidence provided. Do not invent facts not present in the context. "
                            + "Reply in plain prose only, no markdown formatting (no asterisks, bullet points, or headers).",
                    user);
        } catch (Exception e) {
            log.warn("Bedrock call failed for deploymentQuestionAnswer, falling back to deterministic reasoning", e);
            return fallback.deploymentQuestionAnswer(context);
        }
    }

    private String callClaude(String operation, String systemPrompt, String userPrompt) {
        long startedAt = System.currentTimeMillis();
        try {
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(model)
                    .system(SystemContentBlock.builder().text(systemPrompt).build())
                    .messages(Message.builder()
                            .role(ConversationRole.USER)
                            .content(ContentBlock.fromText(userPrompt))
                            .build())
                    .inferenceConfig(InferenceConfiguration.builder().maxTokens(500).build())
                    .build();
            ConverseResponse response = client.converse(request);
            recordUsage(operation, response, startedAt);
            return response.output().message().content().stream()
                    .map(ContentBlock::text)
                    .filter(text -> text != null && !text.isBlank())
                    .reduce((left, right) -> left + right)
                    .orElseThrow(() -> new IllegalStateException("Bedrock response contained no text content"));
        } catch (BedrockRuntimeException e) {
            aiUsageService.record(operation, model, 0, 0, System.currentTimeMillis() - startedAt, false);
            throw new RuntimeException("Bedrock Converse call failed: " + e.getMessage(), e);
        }
    }

    private void recordUsage(String operation, ConverseResponse response, long startedAt) {
        int inputTokens = response.usage() != null && response.usage().inputTokens() != null
                ? response.usage().inputTokens() : 0;
        int outputTokens = response.usage() != null && response.usage().outputTokens() != null
                ? response.usage().outputTokens() : 0;
        long latencyMs = response.metrics() != null && response.metrics().latencyMs() != null
                ? response.metrics().latencyMs()
                : System.currentTimeMillis() - startedAt;
        aiUsageService.record(operation, model, inputTokens, outputTokens, latencyMs, true);
    }

    private String formatReasons(List<RiskReason> reasons) {
        if (reasons.isEmpty()) {
            return "- no correlated risk signals\n";
        }
        StringBuilder builder = new StringBuilder();
        for (RiskReason reason : reasons) {
            builder.append("- [").append(reason.category()).append("] ").append(reason.evidence())
                    .append(" (impact ").append(reason.impact()).append(")\n");
        }
        return builder.toString();
    }
}
