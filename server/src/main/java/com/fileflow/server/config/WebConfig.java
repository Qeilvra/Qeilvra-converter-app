package com.fileflow.server.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfig {
    @Bean
    CorsFilter corsFilter(ProcessingProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.origins());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Content-Type", "Accept"));
        configuration.setExposedHeaders(java.util.List.of(
                "Content-Disposition", "X-FileFlow-Original-Size", "X-FileFlow-Output-Size",
                "X-FileFlow-Reduced", "X-FileFlow-Images-Optimized"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return new CorsFilter(source);
    }

    @Bean
    Filter securityHeaders() {
        return (request, response, chain) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("Cache-Control", "no-store, max-age=0");
            http.setHeader("Referrer-Policy", "no-referrer");
            http.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
            chain.doFilter(request, response);
        };
    }
}
