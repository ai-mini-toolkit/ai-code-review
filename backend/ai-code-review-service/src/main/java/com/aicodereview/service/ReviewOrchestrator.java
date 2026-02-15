package com.aicodereview.service;

import com.aicodereview.common.dto.review.ReviewResult;
import com.aicodereview.common.dto.reviewtask.CodeContext;
import com.aicodereview.common.exception.AIProviderException;
import com.aicodereview.common.exception.ResourceNotFoundException;
import com.aicodereview.integration.ai.AIProvider;
import com.aicodereview.integration.ai.AIProviderFactory;
import com.aicodereview.repository.PromptTemplateRepository;
import com.aicodereview.repository.entity.PromptTemplate;
import com.aicodereview.repository.entity.ReviewTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the code review flow: context assembly, prompt rendering,
 * AI provider call with fallback degradation strategy.
 * <p>
 * Three-level degradation:
 * <ul>
 *   <li>Level 0: Primary provider (from {@code ai.provider.default})</li>
 *   <li>Level 1: Fallback provider (from {@code ai.provider.fallback})</li>
 *   <li>Level 2: Complete failure → {@code ReviewResult.failed()}</li>
 * </ul>
 * Providers handle their own internal retries (429/5xx). This orchestrator
 * only manages cross-provider fallback.
 * </p>
 *
 * @since 4.4.0
 */
@Service
@Slf4j
public class ReviewOrchestrator {

    private static final String CODE_REVIEW_CATEGORY = "code-review";
    private static final Handlebars HANDLEBARS = new Handlebars();

    private final ReviewContextAssembler contextAssembler;
    private final PromptTemplateRepository promptTemplateRepository;
    private final AIProviderFactory providerFactory;
    private final ObjectMapper objectMapper;
    private final String fallbackProviderId;

    private final Timer reviewDurationTimer;
    private final Counter reviewSuccessCounter;
    private final Counter reviewFailureCounter;
    private final MeterRegistry meterRegistry;

    public ReviewOrchestrator(
            ReviewContextAssembler contextAssembler,
            PromptTemplateRepository promptTemplateRepository,
            AIProviderFactory providerFactory,
            MeterRegistry meterRegistry,
            @Value("${ai.provider.fallback:anthropic}") String fallbackProviderId) {
        this.contextAssembler = contextAssembler;
        this.promptTemplateRepository = promptTemplateRepository;
        this.providerFactory = providerFactory;
        this.meterRegistry = meterRegistry;
        this.fallbackProviderId = fallbackProviderId;
        this.objectMapper = new ObjectMapper();

        this.reviewDurationTimer = Timer.builder("ai.review.duration")
                .description("Total review duration including fallback")
                .register(meterRegistry);
        this.reviewSuccessCounter = Counter.builder("ai.review.success")
                .description("Successful review count")
                .register(meterRegistry);
        this.reviewFailureCounter = Counter.builder("ai.review.failure")
                .description("Failed review count")
                .register(meterRegistry);
    }

    /**
     * Executes a complete code review for the given task.
     *
     * @param task the review task to process
     * @return structured review result (success or failed with degradation events)
     */
    public ReviewResult review(ReviewTask task) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Step 1: Assemble code context
            CodeContext codeContext = contextAssembler.assembleContext(task);

            // Step 2: Load and render prompt template
            String renderedPrompt = loadAndRenderPrompt(codeContext);

            // Step 3: Execute with provider fallback
            ReviewResult result = executeWithFallback(codeContext, renderedPrompt);

            if (result.isSuccess()) {
                reviewSuccessCounter.increment();
            } else {
                reviewFailureCounter.increment();
            }

            return result;
        } catch (ResourceNotFoundException e) {
            // No enabled prompt template found
            log.error("Review failed: {}", e.getMessage());
            reviewFailureCounter.increment();
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during review orchestration: {}", e.getMessage(), e);
            reviewFailureCounter.increment();
            return ReviewResult.failed("Review orchestration failed: " + e.getMessage());
        } finally {
            sample.stop(reviewDurationTimer);
        }
    }

    private String loadAndRenderPrompt(CodeContext codeContext) {
        List<PromptTemplate> templates = promptTemplateRepository
                .findByCategoryAndEnabled(CODE_REVIEW_CATEGORY, true);

        if (templates.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No enabled PromptTemplate found for category: " + CODE_REVIEW_CATEGORY);
        }

        PromptTemplate template = templates.get(0);
        log.info("Using prompt template: id={}, name={}", template.getId(), template.getName());

        try {
            Map<String, Object> contextMap = buildTemplateContext(codeContext);
            Template compiled = HANDLEBARS.compileInline(template.getTemplateContent());
            return compiled.apply(contextMap);
        } catch (Exception e) {
            log.error("Failed to render prompt template: {}", e.getMessage());
            throw new IllegalStateException("Failed to render prompt template: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildTemplateContext(CodeContext codeContext) {
        Map<String, Object> contextMap = new HashMap<>();
        try {
            contextMap.put("rawDiff", codeContext.getRawDiff() != null ? codeContext.getRawDiff() : "");
            contextMap.put("files", codeContext.getFiles() != null
                    ? objectMapper.writeValueAsString(codeContext.getFiles()) : "[]");
            contextMap.put("statistics", codeContext.getStatistics() != null
                    ? objectMapper.writeValueAsString(codeContext.getStatistics()) : "{}");
            contextMap.put("taskMeta", codeContext.getTaskMeta() != null
                    ? objectMapper.writeValueAsString(codeContext.getTaskMeta()) : "{}");
        } catch (Exception e) {
            log.error("Failed to serialize CodeContext for template: {}", e.getMessage());
            throw new AIProviderException("Failed to serialize CodeContext: " + e.getMessage(), e);
        }
        return contextMap;
    }

    private ReviewResult executeWithFallback(CodeContext codeContext, String renderedPrompt) {
        List<String> degradationEvents = new ArrayList<>();

        // Level 0: Primary provider
        AIProvider primaryProvider = providerFactory.getDefaultProvider();
        String primaryId = primaryProvider.getProviderId();

        try {
            log.info("Attempting review with primary provider: {}", primaryId);
            ReviewResult result = primaryProvider.analyze(codeContext, renderedPrompt);

            Counter.builder("ai.review.provider.used")
                    .tag("provider", primaryId)
                    .register(meterRegistry)
                    .increment();

            return result;
        } catch (AIProviderException e) {
            log.warn("Primary provider '{}' failed: {}", primaryId, e.getMessage());
            degradationEvents.add("Primary provider '" + primaryId + "' failed: " + e.getMessage());
        }

        // Level 1: Fallback provider
        AIProvider resolvedFallback = resolveFallbackProvider(primaryId);
        if (resolvedFallback != null) {
            Counter.builder("ai.review.degradation")
                    .tag("from", primaryId)
                    .tag("to", fallbackProviderId)
                    .register(meterRegistry)
                    .increment();

            try {
                log.info("Attempting review with fallback provider: {}", fallbackProviderId);
                ReviewResult result = resolvedFallback.analyze(codeContext, renderedPrompt);

                Counter.builder("ai.review.provider.used")
                        .tag("provider", fallbackProviderId)
                        .register(meterRegistry)
                        .increment();

                // Merge degradation events into metadata
                if (result.isSuccess() && result.getMetadata() != null) {
                    result.getMetadata().getDegradationEvents().addAll(degradationEvents);
                }

                return result;
            } catch (AIProviderException e) {
                log.error("Fallback provider '{}' also failed: {}", fallbackProviderId, e.getMessage());
                degradationEvents.add("Fallback provider '" + fallbackProviderId + "' failed: " + e.getMessage());
            }
        } else {
            log.info("Skipping fallback: fallbackId='{}', primaryId='{}'", fallbackProviderId, primaryId);
            degradationEvents.add("Fallback skipped: provider '" + fallbackProviderId
                    + "' is same as primary or unavailable");
        }

        // Level 2: Complete failure
        log.error("All AI providers failed. Degradation chain: {}", degradationEvents);
        return ReviewResult.failed("All AI providers failed. Degradation chain: " + degradationEvents);
    }

    private AIProvider resolveFallbackProvider(String primaryId) {
        // Skip if fallback is same as primary
        if (fallbackProviderId.equals(primaryId)) {
            return null;
        }
        // Skip if fallback provider is not registered or not available
        try {
            AIProvider fallback = providerFactory.getProvider(fallbackProviderId);
            return fallback.isAvailable() ? fallback : null;
        } catch (Exception e) {
            log.warn("Fallback provider '{}' not registered: {}", fallbackProviderId, e.getMessage());
            return null;
        }
    }
}
