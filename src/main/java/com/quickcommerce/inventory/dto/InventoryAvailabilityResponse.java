package com.quickcommerce.inventory.dto;

import com.quickcommerce.inventory.domain.InventoryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for inventory availability check
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAvailabilityResponse {

    private Long storeId;
    private List<ProductAvailability> products;
    private StoreInfo storeInfo; // Optional: Only populated for nearest-store queries

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StoreInfo {
        private String storeName;
        private String storeAddress;
        private Double distanceKm;
        private Integer estimatedDeliveryMinutes;
        private Boolean isServiceable;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductAvailability {
        private String sku;
        private Long productId;
        private Integer currentStock;
        private Integer availableStock;
        private Integer reservedStock;
        private Integer safetyStock;
        private Boolean inStock;
        private Boolean lowStock;
        private String availabilityStatus; // AVAILABLE, LOW_STOCK, OUT_OF_STOCK
    }

    public static InventoryAvailabilityResponse fromInventoryItems(Long storeId, List<InventoryItem> items) {
        List<ProductAvailability> productAvailabilities = items.stream()
                .map(item -> ProductAvailability.builder()
                        .sku(item.getSku())
                        .productId(item.getProductId())
                        .currentStock(item.getCurrentStock())
                        .availableStock(item.getAvailableStock())
                        .reservedStock(item.getReservedStock())
                        .safetyStock(item.getSafetyStock())
                        .inStock(item.getAvailableStock() > 0)
                        .lowStock(item.isLowStock())
                        .availabilityStatus(determineAvailabilityStatus(item))
                        .build())
                .toList();

        return InventoryAvailabilityResponse.builder()
                .storeId(storeId)
                .products(productAvailabilities)
                .build();
    }

    private static String determineAvailabilityStatus(InventoryItem item) {
        int available = item.getAvailableStock();
        int safety = item.getSafetyStock();

        if (available <= 0) {
            return "OUT_OF_STOCK";
        } else if (available <= safety) {
            return "LOW_STOCK";
        } else {
            return "AVAILABLE";
        }
    }
}
