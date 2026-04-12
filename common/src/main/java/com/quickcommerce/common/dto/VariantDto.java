package com.quickcommerce.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Minimal variant representation for the bottom sheet UI.
 * Shared across product-service and search-service to ensure a consistent JSON contract.
 *
 * Sorted by base_price ASC at the DB level, so the first element is always the cheapest
 * variant — works universally for volume, weight, pack count, and unit-based variants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantDto {

    private Long productId;

    private String sku;

    /** Display label — maps to Product.packageSize (e.g. "500 ml", "1 Litre", "Pack of 4") */
    private String size;

    /** Selling price — maps to Product.basePrice */
    private BigDecimal price;

    /** Stock availability — maps to Product.isAvailable. JSON key: "inStock" */
    private Boolean inStock;
}
