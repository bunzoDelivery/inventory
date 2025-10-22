package com.quickcommerce.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    private String shortDescription;

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    private String brand;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal basePrice;

    @NotBlank(message = "Unit of measure is required")
    private String unitOfMeasure;

    private String packageSize;

    private String images;

    private String tags;

    private Boolean isActive = true;

    private Boolean isAvailable = true;

    @NotBlank(message = "Slug is required")
    private String slug;

    private String nutritionalInfo;

    private Integer weightGrams;

    private String barcode;
}
