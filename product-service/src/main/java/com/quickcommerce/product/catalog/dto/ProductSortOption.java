package com.quickcommerce.product.catalog.dto;

import com.quickcommerce.product.catalog.domain.Product;
import org.springframework.data.domain.Sort;

/**
 * Sort options for the category products endpoint.
 * Carries its own Sort object built from compile-time field constants — no magic strings.
 */
public enum ProductSortOption {

    PRICE_ASC(Sort.by(Sort.Order.asc(Product.Fields.basePrice))),
    PRICE_DESC(Sort.by(Sort.Order.desc(Product.Fields.basePrice)));

    private static final Sort DEFAULT_SORT = Sort.by(Product.Fields.name, Product.Fields.id);

    private final Sort sort;

    ProductSortOption(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() {
        return this.sort;
    }

    /**
     * Returns the Sort for the given option, or name/id ordering when null.
     */
    public static Sort resolve(ProductSortOption sortBy) {
        return sortBy != null ? sortBy.toSort() : DEFAULT_SORT;
    }
}
