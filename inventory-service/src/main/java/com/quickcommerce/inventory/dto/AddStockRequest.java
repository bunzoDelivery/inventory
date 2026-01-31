package com.quickcommerce.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding stock to inventory
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddStockRequest {

    @NotBlank(message = "SKU is required")
    private String sku;

    @NotNull(message = "Store ID is required")
    private Long storeId;

    private Long productId;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Integer quantity;

    private String reason;

    private String referenceId;
}
