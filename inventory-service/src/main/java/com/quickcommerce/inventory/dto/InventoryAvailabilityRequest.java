package com.quickcommerce.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for inventory availability check
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAvailabilityRequest {

    @NotNull(message = "Store ID cannot be null")
    private Long storeId;

    @NotEmpty(message = "SKUs list cannot be empty")
    private List<String> skus;
}
