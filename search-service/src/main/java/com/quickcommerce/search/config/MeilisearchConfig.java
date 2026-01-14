package com.quickcommerce.search.config;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Meilisearch client
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MeilisearchConfig {

    private final MeilisearchProperties properties;

    /**
     * Creates and configures Meilisearch client bean
     */
    @Bean
    public Client meilisearchClient() {
        log.info("Initializing Meilisearch client with host: {}", properties.getHost());
        
        Config config = new Config(
            properties.getHost(),
            properties.getApiKey()
        );
        
        return new Client(config);
    }
}
