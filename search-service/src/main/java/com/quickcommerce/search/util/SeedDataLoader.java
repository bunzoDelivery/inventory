package com.quickcommerce.search.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickcommerce.search.model.ProductDocument;
import com.quickcommerce.search.provider.MeilisearchProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Loads seed data into Meilisearch index for local development
 * Only runs in 'dev' profile
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SeedDataLoader {

    private final MeilisearchProvider meilisearchProvider;
    private final ObjectMapper objectMapper;

    /**
     * Loads sample products into Meilisearch on application startup
     */
    @PostConstruct
    public void loadSeedData() {
        log.info("Loading seed data for development...");

        try {
            // Check if index exists, create if not
            try {
                meilisearchProvider.getProductsIndex().getStats();
                log.info("Index already exists");
            } catch (Exception e) {
                log.info("Index does not exist, creating...");
                meilisearchProvider.createIndex();
            }

            // Load sample products from JSON file
            ClassPathResource resource = new ClassPathResource("seed-data/sample-products.json");
            
            try (InputStream inputStream = resource.getInputStream()) {
                List<ProductDocument> products = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<ProductDocument>>() {}
                );

                log.info("Loaded {} sample products from JSON", products.size());

                // Upsert products to Meilisearch
                meilisearchProvider.upsertDocuments(products);

                log.info("✓ Seed data loaded successfully: {} products indexed", products.size());
                
            } catch (IOException e) {
                log.error("Failed to read seed data file", e);
            }

        } catch (Exception e) {
            log.error("Failed to load seed data", e);
        }
    }
}
