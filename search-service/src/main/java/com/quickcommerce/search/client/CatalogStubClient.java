package com.quickcommerce.search.client;

import com.quickcommerce.search.model.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub implementation of CatalogClient for local development
 * Returns dummy bestsellers for fallback testing
 */
@Slf4j
@Component
@Profile("dev")
public class CatalogStubClient implements CatalogClient {

    @Override
    public Mono<List<ProductDocument>> getBestsellers(Long storeId, int limit) {
        log.debug("STUB: Getting {} bestsellers for store {}", limit, storeId);

        // Return some dummy bestsellers for testing fallback
        ProductDocument p1 = new ProductDocument();
        p1.setId(99L);
        p1.setName("Bestseller - Maggi Noodles");
        p1.setBrand("Maggi");
        p1.setPrice(java.math.BigDecimal.valueOf(12.0));
        p1.setCategoryName("Snacks");
        p1.setStoreIds(List.of(storeId));
        p1.setIsActive(true);
        p1.setIsBestseller(true);

        ProductDocument p2 = new ProductDocument();
        p2.setId(100L);
        p2.setName("Bestseller - Coke Can");
        p2.setBrand("Coca Cola");
        p2.setPrice(java.math.BigDecimal.valueOf(40.0));
        p2.setCategoryName("Beverages");
        p2.setStoreIds(List.of(storeId));
        p2.setIsActive(true);
        p2.setIsBestseller(true);

        return Mono.just(List.of(p1, p2));
    }

    @Override
    public Mono<List<ProductDocument>> getProductsByCategory(Long categoryId, int limit) {
        log.debug("STUB: Getting {} products from category {}", limit, categoryId);

        // Stub: return empty list
        log.debug("STUB: Returning empty category products list");

        return Mono.just(new ArrayList<>());
    }
}
