package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO mirroring catalog API (ProductResponse) for deserialization.
 * CatalogClient returns this; callers map to ProductDocument.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductDto {

    private Long id;
    private String sku;
    private String name;
    private String description;
    private String shortDescription;
    private Long categoryId;
    private String brand;
    private BigDecimal basePrice;
    private String unitOfMeasure;
    private String packageSize;
    private String images;
    private String tags;
    private Boolean isActive;
    private Boolean isAvailable;
    private String slug;
    private String nutritionalInfo;
    private Integer weightGrams;
    private String barcode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
