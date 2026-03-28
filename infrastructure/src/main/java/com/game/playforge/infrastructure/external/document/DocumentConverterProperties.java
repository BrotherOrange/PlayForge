package com.game.playforge.infrastructure.external.document;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Gemini document conversion configuration.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.document-converter")
public class DocumentConverterProperties {

    /**
     * Base URL for the Gemini API.
     */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * Gemini model name used for document normalization.
     */
    private String modelName = "gemini-3.1-flash-lite-preview";

    /**
     * Request timeout for Gemini normalization calls.
     */
    private Duration timeout = Duration.ofSeconds(180);
}
