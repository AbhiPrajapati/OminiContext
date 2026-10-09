package com.omnicontext.exception;

/**
 * Thrown when an AI provider (OpenAI, LM Studio, Ollama) times out or returns an error.
 */
public class AiProviderTimeoutException extends RuntimeException {

    private final String provider;

    public AiProviderTimeoutException(String provider, String message) {
        super("AI provider [" + provider + "] error: " + message);
        this.provider = provider;
    }

    public AiProviderTimeoutException(String provider, String message, Throwable cause) {
        super("AI provider [" + provider + "] error: " + message, cause);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
