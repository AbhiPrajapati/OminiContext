package com.omnicontext.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Global Cross-Origin Resource Sharing (CORS) Configuration.
 * 
 * Supports:
 *  - Localhost on any port (HTTP & HTTPS) and 127.0.0.1 loopback
 *  - Chrome browser extension origins (chrome-extension://*)
 *  - Configurable external production domains via `cors.allowed-origins`
 *  - Pre-flight OPTIONS handling at HIGHEST_PRECEDENCE via CorsFilter bean
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:3000,http://localhost:8080,http://localhost:5173,https://app.omnicontext.io}")
    private String allowedOriginsRaw;

    private List<String> buildOriginPatterns() {
        List<String> patterns = new ArrayList<>(Arrays.asList(
                "http://localhost",
                "http://localhost:*",
                "https://localhost",
                "https://localhost:*",
                "http://127.0.0.1",
                "http://127.0.0.1:*",
                "https://127.0.0.1",
                "https://127.0.0.1:*",
                "chrome-extension://*"
        ));

        if (allowedOriginsRaw != null && !allowedOriginsRaw.isBlank()) {
            for (String origin : allowedOriginsRaw.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !patterns.contains(trimmed)) {
                    patterns.add(trimmed);
                }
            }
        }
        return patterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] originPatterns = buildOriginPatterns().toArray(new String[0]);

        registry.addMapping("/**")
                .allowedOriginPatterns(originPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("X-Total-Count", "Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        for (String pattern : buildOriginPatterns()) {
            config.addAllowedOriginPattern(pattern);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-Total-Count");
        config.addExposedHeader("Trace-Id");
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
