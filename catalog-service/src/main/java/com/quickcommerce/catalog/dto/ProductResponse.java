package com.quickcommerce.catalog.dto;

import com.quickcommerce.catalog.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

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

    public static ProductResponse fromDomain(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getShortDescription(),
                product.getCategoryId(),
                product.getBrand(),
                product.getBasePrice(),
                product.getUnitOfMeasure(),
                product.getPackageSize(),
                product.getImages(),
                product.getTags(),
                product.getIsActive(),
                product.getIsAvailable(),
                product.getSlug(),
                product.getNutritionalInfo(),
                product.getWeightGrams(),
                product.getBarcode(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
