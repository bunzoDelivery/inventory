package com.quickcommerce.catalog.dto;

import com.quickcommerce.catalog.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String slug;
    private Integer displayOrder;
    private Boolean isActive;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResponse fromDomain(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getParentId(),
                category.getSlug(),
                category.getDisplayOrder(),
                category.getIsActive(),
                category.getImageUrl(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
