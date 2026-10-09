package com.omnicontext.config;

import com.omnicontext.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter filter applied to high-cost endpoints.
 * Uses in-memory Bucket4j (can be swapped to Redis-backed for distributed deployments).
 *
 * Rate limits are per IP address:
 *  - POST /api/contexts/preview-compression: configurable RPM (default 10/min)
 *  - POST /api/contexts (create): configurable RPM (default 30/min)
 */
@Component
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int compressionRpm;
    private final int createRpm;

    private final Map<String, Bucket> compressionBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> createBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${ratelimit.compression.requests-per-minute:10}") int compressionRpm,
            @Value("${ratelimit.create.requests-per-minute:30}") int createRpm) {
        this.compressionRpm = compressionRpm;
        this.createRpm = createRpm;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = resolveClientIp(request);

        // Rate limit POST /api/contexts/preview-compression
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/contexts/preview-compression")) {
            Bucket bucket = compressionBuckets.computeIfAbsent(clientIp,
                    k -> buildBucket(compressionRpm));
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for preview-compression from IP: {}", clientIp);
                throw new RateLimitExceededException(
                        "Too many compression preview requests. Limit: " + compressionRpm + " per minute.", 60);
            }
        }

        // Rate limit POST /api/contexts (exact path, not sub-paths)
        if ("POST".equalsIgnoreCase(method) && path.equals("/api/contexts")) {
            Bucket bucket = createBuckets.computeIfAbsent(clientIp,
                    k -> buildBucket(createRpm));
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for context creation from IP: {}", clientIp);
                throw new RateLimitExceededException(
                        "Too many context creation requests. Limit: " + createRpm + " per minute.", 60);
            }
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private Bucket buildBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(requestsPerMinute)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
