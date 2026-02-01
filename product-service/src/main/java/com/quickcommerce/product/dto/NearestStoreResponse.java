package com.quickcommerce.product.dto;

import com.quickcommerce.product.domain.Store;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for nearest store query with inventory availability
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearestStoreResponse {

    private Long storeId;
    private String storeName;
    private String storeAddress;
    private Double distanceKm;
    private Integer estimatedDeliveryMinutes;
    private Boolean isServiceable;
    private InventoryAvailabilityResponse inventoryAvailability;

    /**
     * Create response from Store entity
     */
    public static NearestStoreResponse fromStore(Store store, Double customerLat, Double customerLon,
                                                  InventoryAvailabilityResponse availability) {
        double distance = store.calculateDistanceKm(customerLat, customerLon);
        int deliveryTime = store.estimateDeliveryTimeMinutes(customerLat, customerLon);
        boolean serviceable = store.isLocationServiceable(customerLat, customerLon);

        return NearestStoreResponse.builder()
                .storeId(store.getId())
                .storeName(store.getName())
                .storeAddress(store.getAddress())
                .distanceKm(Math.round(distance * 100.0) / 100.0) // Round to 2 decimal places
                .estimatedDeliveryMinutes(deliveryTime)
                .isServiceable(serviceable)
                .inventoryAvailability(availability)
                .build();
    }
}
