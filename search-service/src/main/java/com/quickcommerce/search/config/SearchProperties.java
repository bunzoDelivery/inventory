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
}
