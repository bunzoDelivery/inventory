package com.quickcommerce.product.catalog.repository;

import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.catalog.dto.ProductSortOption;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Custom repository fragment for dynamic product queries.
 * Implemented by ProductRepositoryCustomImpl using R2dbcEntityTemplate + Criteria API.
 */
public interface ProductRepositoryCustom {

    Flux<Product> findByCategoryWithFilters(Long categoryId, ProductSortOption sortBy,
                                            String brand, int limit, long offset);

    Mono<Long> countByCategoryWithFilters(Long categoryId, String brand);
}
