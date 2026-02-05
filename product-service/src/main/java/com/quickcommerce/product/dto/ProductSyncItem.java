package com.quickcommerce.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO representing a single product with inventory data for bulk sync
 * Used in store-centric bulk sync operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSyncItem {
    
    // ============ Product Metadata ============
    
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
    
    private String slug;
    
    private String nutritionalInfo;
    
    private Integer weightGrams;
    
    private String barcode;
    
    // ============ Inventory Data ============
    // Note: storeId is at request level, not per item
    
    @NotNull(message = "Current stock is required")
    @Min(value = 0, message = "Current stock cannot be negative")
    private Integer currentStock;
    
    @Min(value = 0, message = "Safety stock cannot be negative")
    private Integer safetyStock = 10;
    
    @Min(value = 1, message = "Max stock must be at least 1")
    private Integer maxStock = 1000;
    
    @DecimalMin(value = "0.0", inclusive = true, message = "Unit cost cannot be negative")
    private BigDecimal unitCost;
}
