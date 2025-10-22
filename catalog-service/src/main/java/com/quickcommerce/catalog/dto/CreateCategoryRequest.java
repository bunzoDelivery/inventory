package com.quickcommerce.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Category name is required")
    private String name;

    private String description;

    private Long parentId;

    @NotBlank(message = "Slug is required")
    private String slug;

    @NotNull(message = "Display order is required")
    private Integer displayOrder;

    private Boolean isActive = true;

    private String imageUrl;
}
