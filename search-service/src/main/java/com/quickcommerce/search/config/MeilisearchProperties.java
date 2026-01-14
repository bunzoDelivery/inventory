package com.quickcommerce.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for Meilisearch
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "meilisearch")
public class MeilisearchProperties {

    /**
     * Meilisearch host URL
     */
    private String host = "http://localhost:7700";

    /**
     * Meilisearch API key (master key)
     */
    private String apiKey = "masterKey";

    /**
     * Index name for products
     */
    private String indexName = "products";

    /**
     * Request timeout duration
     */
    private Duration timeout = Duration.ofMillis(500);
}
