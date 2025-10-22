package com.quickcommerce.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for stock reservation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveStockRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotBlank(message = "Order ID is required")
    private String orderId;
}
