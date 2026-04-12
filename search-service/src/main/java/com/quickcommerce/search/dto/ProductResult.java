package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Individual product result in search response.
 * Aligned with ProductResponse (catalog API) for consistency.
 * Use GET /api/v1/catalog/products/groups/batch to fetch variant data for the bottom sheet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResult {

    /**
     * Product ID
     */
    private Long productId;

    /**
     * Stock Keeping Unit
     */
    private String sku;

    /**
     * Group identifier to connect related variants
     */
    private String groupId;

    /**
     * Product name
     */
    private String name;

    /**
     * Brand name
     */
    private String brand;

    /**
     * Category ID
     */
    private Long categoryId;

    /**
     * Base price
     */
    private BigDecimal basePrice;

    /**
     * Unit of measure (e.g., "kg", "L", "piece")
     */
    private String unitOfMeasure;

    /**
     * Package size (e.g., "1L", "500g")
     */
    private String packageSize;

    /**
     * Product image r2Keys (JSON array, same as catalog)
     */
    private List<String> images;

    /**
     * Product slug (URL-friendly identifier)
     */
    private String slug;

    /**
     * Stock availability flag (search-specific)
     */
    private Boolean inStock;
}
