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

    /**
     * How Meilisearch relevance blends with catalog business signals (order_count, search_priority, bestseller).
     * Tune via {@code search.ranking.*} — see {@link Ranking}.
     */
    private Ranking ranking = new Ranking();

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

    /**
     * Composite ranking: primary = Meilisearch {@code _rankingScore} (or hit order), secondary = business score.
     * Adjust weights here or in YAML without changing algorithm code.
     */
    @Data
    public static class Ranking {

        /**
         * Portion of final score from relevance (0–1). Complements {@link #businessWeight}.
         */
        private double relevanceWeight = 0.65;

        /**
         * Portion of final score from business signals. Complements {@link #relevanceWeight}.
         */
        private double businessWeight = 0.35;

        /**
         * Relative importance of order_count inside the business component (sums with other two).
         */
        private double orderCountWeight = 0.5;

        /**
         * Relative importance of search_priority (0–100 scale in catalog).
         */
        private double searchPriorityWeight = 0.35;

        /**
         * Relative importance of bestseller flag inside business component.
         */
        private double bestsellerWeight = 0.15;
    }
}
