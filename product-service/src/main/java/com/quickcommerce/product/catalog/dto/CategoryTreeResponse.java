package com.quickcommerce.product.catalog.dto;

import com.quickcommerce.product.catalog.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for hierarchical category tree structure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeResponse {

    private Long id;
    private String name;
    private String description;
    private String slug;
    private Integer displayOrder;
    private Boolean isActive;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CategoryTreeResponse> children;

    public static CategoryTreeResponse fromDomain(Category category) {
        return new CategoryTreeResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getSlug(),
                category.getDisplayOrder(),
                category.getIsActive(),
                category.getImageUrl(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                new ArrayList<>()
        );
    }

    public void addChild(CategoryTreeResponse child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}
