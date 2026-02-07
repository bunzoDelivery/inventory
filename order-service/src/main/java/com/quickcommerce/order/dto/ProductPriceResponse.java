package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceResponse {
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
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    
    // Helper method to get price
    public BigDecimal getPrice() {
        return basePrice;
    }
}
