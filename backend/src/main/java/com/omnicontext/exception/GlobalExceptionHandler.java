package com.omnicontext.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Global exception handler implementing RFC 7807 ProblemDetail responses.
 * Provides consistent error schema across all clients (Extension, Watcher, Frontend).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Resource not found: {}", traceId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://omnicontext.io/errors/not-found"));
        problem.setProperty("traceId", traceId);
        problem.setProperty("resourceType", ex.getResourceType());
        problem.setProperty("resourceId", ex.getResourceId());
        return problem;
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Rate limit exceeded: {}", traceId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Rate Limit Exceeded");
        problem.setType(URI.create("https://omnicontext.io/errors/rate-limit"));
        problem.setProperty("traceId", traceId);
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return problem;
    }

    @ExceptionHandler(AiProviderTimeoutException.class)
    public ProblemDetail handleAiProviderTimeout(AiProviderTimeoutException ex) {
        String traceId = generateTraceId();
        log.error("[{}] AI provider failure: {}", traceId, ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI distillation service is temporarily unavailable. Rule-based NLP compression was used as fallback.");
        problem.setTitle("AI Provider Unavailable");
        problem.setType(URI.create("https://omnicontext.io/errors/ai-provider-timeout"));
        problem.setProperty("traceId", traceId);
        problem.setProperty("provider", ex.getProvider());
        return problem;
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Payload too large: {}", traceId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setTitle("Payload Too Large");
        problem.setType(URI.create("https://omnicontext.io/errors/payload-too-large"));
        problem.setProperty("traceId", traceId);
        problem.setProperty("maxBytes", ex.getMaxBytes());
        problem.setProperty("actualBytes", ex.getActualBytes());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String traceId = generateTraceId();

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));

        log.warn("[{}] Validation failed: {}", traceId, errors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed: " + errors);
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://omnicontext.io/errors/validation"));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        String traceId = generateTraceId();
        log.warn("[{}] Bad request: {}", traceId, ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        problem.setType(URI.create("https://omnicontext.io/errors/bad-request"));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        String traceId = generateTraceId();
        log.error("[{}] Unhandled exception: {}", traceId, ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support with trace ID: " + traceId);
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://omnicontext.io/errors/internal"));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
