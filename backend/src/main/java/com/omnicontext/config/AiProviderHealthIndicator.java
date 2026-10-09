package com.omnicontext.config;

import com.omnicontext.service.ContextCompressionEngine;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom readiness health indicator that checks:
 * 1. AI provider availability (OpenAI / LM Studio / Ollama)
 *
 * MongoDB connectivity is already checked by Spring Boot's built-in MongoHealthIndicator.
 */
@Component("aiProvider")
public class AiProviderHealthIndicator implements HealthIndicator {

    private final ContextCompressionEngine compressionEngine;

    public AiProviderHealthIndicator(ContextCompressionEngine compressionEngine) {
        this.compressionEngine = compressionEngine;
    }

    @Override
    public Health health() {
        var aiStatus = compressionEngine.getAiStatus();
        boolean online = Boolean.TRUE.equals(aiStatus.get("online"));
        String provider = (String) aiStatus.getOrDefault("provider", "NONE");

        Health.Builder builder = online ? Health.up() : Health.down();
        builder.withDetail("provider", provider);
        builder.withDetail("model", aiStatus.getOrDefault("model", "unknown"));
        builder.withDetail("url", aiStatus.getOrDefault("url", ""));

        // AI being offline is degraded but not fatal — rule-based NLP is the fallback
        if (!online) {
            builder.withDetail("fallback", "RULE_BASED_NLP");
            return Health.up()
                    .withDetail("provider", provider)
                    .withDetail("status", "DEGRADED")
                    .withDetail("fallback", "RULE_BASED_NLP")
                    .build();
        }

        return builder.build();
    }
}
