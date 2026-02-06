package com.quickcommerce.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for search functionality
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    /**
     * Number of candidates to fetch from Meilisearch before filtering
     */
    private int candidateLimit = 80;

    /**
     * Default number of results to return
     */
    private int defaultResultLimit = 20;

    /**
     * Maximum allowed result limit
     */
    private int maxResultLimit = 100;

    /**
     * Sync configuration
     */
    private Sync sync = new Sync();

    @Data
    public static class Sync {
        /**
         * Enable automatic sync on startup
         */
        private boolean enableOnStartup = true;

        /**
         * Batch size for syncing products
         */
        private int batchSize = 500;

        /**
         * Max retry attempts for sync operations
         */
        private int maxRetries = 3;

        /**
         * Initial delay for retry in milliseconds
         */
        private long retryDelayMs = 1000;

        /**
         * Maximum delay for retry in milliseconds
         */
        private long maxRetryDelayMs = 10000;
    }
}
