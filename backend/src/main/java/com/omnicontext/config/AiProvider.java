package com.omnicontext.config;

/**
 * Configurable AI provider selection.
 * Set via AI_PROVIDER env var or ai.provider property.
 *
 * - OPENAI: Force OpenAI Cloud (requires api key)
 * - LOCAL_LLM: Force local LM Studio / Ollama / vLLM
 * - AUTO: Try OpenAI if key is set, then local LLM, then rule-based NLP fallback
 */
public enum AiProvider {
    OPENAI,
    LOCAL_LLM,
    AUTO;

    /**
     * Parse from string value, defaulting to AUTO for unknown values.
     */
    public static AiProvider fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return AiProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
