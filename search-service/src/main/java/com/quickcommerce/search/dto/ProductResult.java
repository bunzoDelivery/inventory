package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Individual product result in search response
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
     * Product name
     */
    private String name;

    /**
     * Brand name
     */
    private String brand;

    /**
     * Category name
     */
    private String category;

    /**
     * Unit text (e.g., "1L", "500g")
     */
    private String unitText;

    /**
     * Product price
     */
    private BigDecimal price;

    /**
     * Product image URL
     */
    private String imageUrl;

    /**
     * Product page URL
     */
    private String productUrl;

    /**
     * Stock availability flag
     */
    private Boolean inStock;
}
