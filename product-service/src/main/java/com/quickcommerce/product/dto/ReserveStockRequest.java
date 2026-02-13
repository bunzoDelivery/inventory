package com.quickcommerce.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for stock reservation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockRequest {

    @NotBlank(message = "Order ID is required")
    private String orderId;

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    /**
     * Store ID for store-scoped inventory lookup. When provided, reserves from this store.
     * Nullable for backward compatibility (falls back to findBySku).
     */
    private Long storeId;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<StockItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockItemRequest {
        @NotBlank(message = "SKU is required")
        private String sku;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;
    }
}
