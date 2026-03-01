package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Individual product result in search response.
 * Aligned with ProductResponse (catalog API) for consistency.
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
     * Product images (full JSON array string, same as catalog)
     */
    private String images;

    /**
     * Product slug (URL-friendly identifier)
     */
    private String slug;

    /**
     * Stock availability flag (search-specific)
     */
    private Boolean inStock;
}
