package com.quickcommerce.product.catalog.dto;

import com.quickcommerce.product.catalog.domain.Product;
import com.quickcommerce.product.util.ImageJsonUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.Builder
public class ProductResponse {

    private Long id;
    private String sku;
    private String groupId;
    private String name;
    private String description;
    private String shortDescription;
    private Long categoryId;
    private String categoryName;
    private String brand;
    private BigDecimal basePrice;
    private String unitOfMeasure;
    private String packageSize;
    private List<String> images;
    private String tags;
    private Boolean isActive;
    private Boolean isAvailable;
    private String slug;
    private String nutritionalInfo;
    private Integer weightGrams;
    private String barcode;
    private String searchKeywords;
    private Integer searchPriority;
    private Boolean isBestseller;
    private Integer orderCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private java.util.List<VariantDto> availableVariants;

    public static ProductResponse fromDomain(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .sku(product.getSku())
                .groupId(product.getGroupId())
                .name(product.getName())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .categoryId(product.getCategoryId())
                .brand(product.getBrand())
                .basePrice(product.getBasePrice())
                .unitOfMeasure(product.getUnitOfMeasure())
                .packageSize(product.getPackageSize())
                .images(ImageJsonUtils.parseImages(product.getImages()))
                .tags(product.getTags())
                .isActive(product.getIsActive())
                .isAvailable(product.getIsAvailable())
                .slug(product.getSlug())
                .nutritionalInfo(product.getNutritionalInfo())
                .weightGrams(product.getWeightGrams())
                .barcode(product.getBarcode())
                .searchKeywords(product.getSearchKeywords())
                .searchPriority(product.getSearchPriority())
                .isBestseller(product.getIsBestseller())
                .orderCount(product.getOrderCount())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
