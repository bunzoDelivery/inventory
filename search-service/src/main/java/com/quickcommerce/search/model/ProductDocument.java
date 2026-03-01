package com.quickcommerce.search.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product document for Meilisearch index
 * Maps product data from database to searchable format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    /**
     * Product ID (primary key from database)
     */
    @JsonProperty("id")
    private Long id;

    /**
     * Stock Keeping Unit
     */
    @JsonProperty("sku")
    private String sku;

    /**
     * Product name
     */
    @JsonProperty("name")
    private String name;

    /**
     * Brand name
     */
    @JsonProperty("brand")
    private String brand;

    /**
     * Product description
     */
    @JsonProperty("description")
    private String description;

    /**
     * Category ID
     */
    @JsonProperty("categoryId")
    private Long categoryId;

    /**
     * Category name (for display)
     */
    @JsonProperty("categoryName")
    private String categoryName;

    /**
     * Search keywords (from search_keywords column)
     * Hindi terms, synonyms, misspellings
     */
    @JsonProperty("keywords")
    private List<String> keywords;

    /**
     * Product barcode
     */
    @JsonProperty("barcode")
    private String barcode;

    /**
     * Store IDs where this product is available
     * Used for filtering by store
     */
    @JsonProperty("storeIds")
    private List<Long> storeIds;

    /**
     * Whether product is active in catalog
     */
    @JsonProperty("isActive")
    private Boolean isActive;

    /**
     * Base price
     */
    @JsonProperty("price")
    private BigDecimal price;

    /**
     * Unit of measure (e.g., "kg", "L", "piece")
     */
    @JsonProperty("unitOfMeasure")
    private String unitOfMeasure;

    /**
     * Package size text (e.g., "1L", "500g")
     */
    @JsonProperty("unitText")
    private String unitText;

    /**
     * Product slug (URL-friendly identifier)
     */
    @JsonProperty("slug")
    private String slug;

    /**
     * Product images (full JSON array string from catalog)
     */
    @JsonProperty("images")
    private String images;

    /**
     * Search priority (0-100, higher = better)
     */
    /**
     * Search priority (0-100, higher = better)
     */
    @JsonProperty("searchPriority")
    private Integer searchPriority;

    /**
     * Bestseller flag
     */
    @JsonProperty("isBestseller")
    private Boolean isBestseller;

    /**
     * Order count (popularity metric)
     */
    @JsonProperty("orderCount")
    private Integer orderCount;
}
