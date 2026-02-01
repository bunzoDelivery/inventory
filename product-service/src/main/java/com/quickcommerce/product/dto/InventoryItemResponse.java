package com.quickcommerce.product.dto;

import com.quickcommerce.product.domain.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for inventory item information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponse {

    private Long id;
    private String sku;
    private Long productId;
    private Long storeId;
    private Integer currentStock;
    private Integer reservedStock;
    private Integer availableStock;
    private Integer safetyStock;
    private Integer maxStock;
    private BigDecimal unitCost;
    private LocalDateTime lastUpdated;
    private boolean lowStock;
    private boolean needsReplenishment;

    /**
     * Convert domain entity to response DTO
     */
    public static InventoryItemResponse fromDomain(InventoryItem item) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .productId(item.getProductId())
                .storeId(item.getStoreId())
                .currentStock(item.getCurrentStock())
                .reservedStock(item.getReservedStock())
                .availableStock(item.getAvailableStock())
                .safetyStock(item.getSafetyStock())
                .maxStock(item.getMaxStock())
                .unitCost(item.getUnitCost())
                .lastUpdated(item.getLastUpdated())
                .lowStock(item.isLowStock())
                .needsReplenishment(item.needsReplenishment())
                .build();
    }
}
