package com.quickcommerce.product.catalog.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Category entity for organizing products hierarchically
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("categories")
public class Category {

    @Id
    private Long id;

    private String name;

    private String description;

    /**
     * Parent category ID for hierarchical categorization
     * NULL for root categories
     */
    private Long parentId;

    /**
     * URL-friendly slug for the category
     */
    private String slug;

    /**
     * Display order for sorting categories
     */
    private Integer displayOrder;

    /**
     * Whether this category is active and visible to customers
     */
    private Boolean isActive;

    /**
     * URL to category image/icon
     */
    private String imageUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
