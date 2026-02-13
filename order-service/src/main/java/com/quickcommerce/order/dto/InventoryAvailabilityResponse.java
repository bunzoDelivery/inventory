package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAvailabilityResponse {

    private Long storeId;
    private List<ProductAvailability> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAvailability {
        private String sku;
        private Integer availableStock;
        private Boolean inStock;
        private String availabilityStatus;
    }
}
