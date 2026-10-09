package com.omnicontext.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnicontext.config.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

@Service
public class LmStudioClient {

    private static final Logger log = LoggerFactory.getLogger(LmStudioClient.class);

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final boolean enabled;
    private final String openAiApiKey;
    private final String openAiModel;
    private final String openAiBaseUrl;
    private final AiProvider aiProvider;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LmStudioClient(
            @Value("${lmstudio.base-url:http://localhost:1234/v1}") String baseUrl,
            @Value("${lmstudio.model:default}") String model,
            @Value("${lmstudio.timeout-seconds:75}") int timeoutSeconds,
            @Value("${lmstudio.enabled:true}") boolean enabled,
            @Value("${openai.api-key:}") String openAiApiKey,
            @Value("${openai.model:gpt-4o-mini}") String openAiModel,
            @Value("${openai.base-url:https://api.openai.com/v1}") String openAiBaseUrl,
            @Value("${ai.provider:AUTO}") String aiProviderStr,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = enabled;
        this.aiProvider = AiProvider.fromString(aiProviderStr);

        // Strictly read openai.api-key from application.properties so OS-level OPENAI_API_KEY environment variable doesn't hijack it
        String explicitKey = "";
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("application.properties");
            if (resource.exists()) {
                Properties props = org.springframework.core.io.support.PropertiesLoaderUtils.loadProperties(resource);
                explicitKey = props.getProperty("openai.api-key", "").trim();
            }
        } catch (Exception ignored) {}

        // If the property is a placeholder like ${OPENAI_API_KEY:}, use the injected value
        if (explicitKey.startsWith("${")) {
            explicitKey = openAiApiKey != null ? openAiApiKey.trim() : "";
        }

        this.openAiApiKey = explicitKey;
        this.openAiModel = (openAiModel != null && !openAiModel.isBlank()) ? openAiModel.trim() : "gpt-4o-mini";
        String cleanOpenAiUrl = (openAiBaseUrl != null && !openAiBaseUrl.isBlank()) ? openAiBaseUrl.trim() : "https://api.openai.com/v1";
        this.openAiBaseUrl = cleanOpenAiUrl.endsWith("/") ? cleanOpenAiUrl.substring(0, cleanOpenAiUrl.length() - 1) : cleanOpenAiUrl;
        this.objectMapper = objectMapper;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        SSLContext sslContext = createSslContext();
        if (sslContext != null) {
            clientBuilder.sslContext(sslContext);
        }
        this.httpClient = clientBuilder.build();

        log.info("AI Provider mode: {} | OpenAI key configured: {} | Local LLM enabled: {}",
                aiProvider, isOpenAiConfigured(), enabled);
    }

    private SSLContext createSslContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            log.warn("Could not initialize SSLContext: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if a non-blank OpenAI API key is configured.
     */
    public boolean isOpenAiConfigured() {
        return !openAiApiKey.isEmpty() && !openAiApiKey.equalsIgnoreCase("your_openai_api_key_here");
    }

    /**
     * Returns the configured AI provider mode.
     */
    public AiProvider getAiProvider() {
        return aiProvider;
    }

    /**
     * Determine if we should use OpenAI based on provider setting.
     */
    private boolean shouldUseOpenAi() {
        return switch (aiProvider) {
            case OPENAI -> isOpenAiConfigured();
            case LOCAL_LLM -> false;
            case AUTO -> isOpenAiConfigured();
        };
    }

    /**
     * Determine if we should try local LLM based on provider setting.
     */
    private boolean shouldUseLocalLlm() {
        return switch (aiProvider) {
            case OPENAI -> false;
            case LOCAL_LLM -> enabled;
            case AUTO -> enabled;
        };
    }

    /**
     * Check if AI provider is available based on the configured provider mode.
     */
    public boolean isAvailable() {
        if (shouldUseOpenAi()) {
            return true;
        }
        if (!shouldUseLocalLlm()) return false;
        return checkLocalLlmAvailability();
    }

    /**
     * Check if local LLM (LM Studio / Ollama / vLLM) is reachable.
     */
    private boolean checkLocalLlmAvailability() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofMillis(1800))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Distill conversation context using the configured AI provider.
     * Provider resolution order depends on ai.provider setting:
     *   OPENAI:    OpenAI only
     *   LOCAL_LLM: Local LLM only (LM Studio / Ollama / vLLM)
     *   AUTO:      OpenAI if key set → Local LLM → empty (rule-based fallback)
     */
    public Optional<String> distillWithAi(String title, String project, String rawContent) {
        // Try OpenAI first (if applicable)
        if (shouldUseOpenAi()) {
            Optional<String> result = callChatCompletion(title, project, rawContent,
                    openAiBaseUrl + "/chat/completions", openAiModel, openAiApiKey, true);
            if (result.isPresent()) return result;
            // If OPENAI-only mode, don't fall through
            if (aiProvider == AiProvider.OPENAI) return Optional.empty();
        }

        // Try local LLM (LM Studio / Ollama / vLLM)
        if (shouldUseLocalLlm() && checkLocalLlmAvailability()) {
            return callChatCompletion(title, project, rawContent,
                    baseUrl + "/chat/completions", resolveModelName(), null, false);
        }

        return Optional.empty();
    }

    /**
     * Unified chat completion call supporting OpenAI, LM Studio, Ollama, and vLLM
     * (all expose OpenAI-compatible /v1/chat/completions endpoints).
     */
    private Optional<String> callChatCompletion(String title, String project, String rawContent,
                                                 String endpointUrl, String activeModel,
                                                 String apiKey, boolean isCloud) {
        try {
            String systemPrompt = """
You are OmniContext AI context distillation engine.
Compress the project discussion into a dense memory capsule with minimal tokens.
Format:
[CTX:%s|%s]
STACK: <frameworks/libraries>
GOAL: <primary goal>
DECISIONS: <architectural choices>
TASKS: <next steps>
MEM: <bullet points of core technical facts>
""".formatted(project != null ? project : "General", title != null ? title : "Context");

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder();
            if (apiKey != null && !apiKey.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + apiKey);
            }

            String providerLabel = isCloud ? "OpenAI" : "Local LLM";
            log.info("Using {} (model: {}, url: {})", providerLabel, activeModel, endpointUrl);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", activeModel);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", "Raw content to distill:\n" + rawContent)
            ));
            body.put("temperature", 0.1);
            body.put("max_tokens", 450);

            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = reqBuilder
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(isCloud ? 45 : timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            log.info("Sending distillation request to {}...", endpointUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    String raw = choices.get(0).path("message").path("content").asText();
                    if (raw != null && !raw.trim().isEmpty()) {
                        String clean = raw;
                        // Strip reasoning tokens if using a thinking model
                        if (clean.contains("</think>")) {
                            clean = clean.substring(clean.indexOf("</think>") + 8).trim();
                        } else if (clean.startsWith("<think>")) {
                            clean = clean.replace("<think>", "").trim();
                        }
                        if (clean.isEmpty()) {
                            clean = raw.trim();
                        }
                        log.info("{} distillation completed successfully ({} chars)", providerLabel, clean.length());
                        return Optional.of(clean);
                    }
                }
            } else {
                log.warn("{} returned HTTP {}: {}", providerLabel, response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.warn("AI distillation failed or timed out: {}. Falling back to Telegraphic NLP.", e.getMessage());
        }

        return Optional.empty();
    }

    public String resolveModelName() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    return data.get(0).path("id").asText("default");
                }
            }
        } catch (Exception ignored) {}
        return model != null && !model.isBlank() ? model : "default";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public String getOpenAiModel() {
        return openAiModel;
    }

    public String getOpenAiBaseUrl() {
        return openAiBaseUrl;
    }
}
