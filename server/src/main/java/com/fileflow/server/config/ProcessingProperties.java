package com.fileflow.server.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fileflow.processing")
public record ProcessingProperties(
        long maxFileBytes,
        int maxPages,
        Duration timeout,
        int concurrency,
        String allowedOrigins,
        String libreOfficeCommand,
        String qpdfCommand,
        String pdfToDocxCommand) {

    public List<String> origins() {
        return Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
