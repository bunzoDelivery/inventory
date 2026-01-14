package com.quickcommerce.catalog.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Product entity representing items available in the catalog
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("products")
public class Product {

    @Id
    private Long id;

    /**
     * Stock Keeping Unit - unique product identifier
     */
    private String sku;

    /**
     * Product name
     */
    private String name;

    /**
     * Detailed product description
     */
    private String description;

    /**
     * Short description for listings
     */
    private String shortDescription;

    /**
     * Category ID this product belongs to
     */
    private Long categoryId;

    /**
     * Brand name
     */
    private String brand;

    /**
     * Base price before promotions/discounts
     */
    private BigDecimal basePrice;

    /**
     * Unit of measure (e.g., "kg", "piece", "liter", "pack")
     */
    private String unitOfMeasure;

    /**
     * Package size (e.g., "500g", "1L", "12 pieces")
     */
    private String packageSize;

    /**
     * JSON array of image URLs
     * Example: ["https://cdn.example.com/product1.jpg",
     * "https://cdn.example.com/product2.jpg"]
     */
    private String images;

    /**
     * Comma-separated tags for search and filtering
     * Example: "organic,gluten-free,vegan"
     */
    private String tags;

    /**
     * Whether this product is active in the catalog
     */
    private Boolean isActive;

    /**
     * Whether this product is currently available for purchase
     * (can be disabled temporarily without removing from catalog)
     */
    private Boolean isAvailable;

    /**
     * URL-friendly slug for product pages
     */
    private String slug;

    /**
     * Nutritional information (optional, JSON format)
     */
    private String nutritionalInfo;

    /**
     * Product weight in grams (for shipping calculation)
     */
    private Integer weightGrams;

    /**
     * Barcode/UPC/EAN for scanning
     */
    private String barcode;

    /**
     * Additional search keywords for enhanced discoverability
     * Hindi terms, synonyms, common misspellings (comma-separated)
     * Example: "doodh,milk,dudh,taaza,tazza"
     */
    private String searchKeywords;

    /**
     * Global search ranking priority (0-100, higher = better)
     * Manual override for boosting specific products in search results
     */
    private Integer searchPriority;

    /**
     * Bestseller flag for ranking boost
     * Manually set by marketing/admin team
     */
    private Boolean isBestseller;

    /**
     * Product view count (passive popularity signal)
     * Incremented when product is viewed
     */
    private Integer viewCount;

    /**
     * Times this product was ordered (strong popularity signal)
     * Incremented on successful orders
     */
    private Integer orderCount;

    /**
     * Last order timestamp (for trending/recency factor)
     */
    private LocalDateTime lastOrderedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
