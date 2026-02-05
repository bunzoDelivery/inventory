package com.quickcommerce.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for bulk product and inventory sync
 * Store-centric design: storeId at top level applies to all items
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkSyncRequest {
    
    @NotNull(message = "Store ID is required")
    private Long storeId;
    
    @NotNull(message = "Items list is required")
    @Size(min = 1, max = 500, message = "Batch size must be between 1 and 500 items")
    @Valid
    private List<ProductSyncItem> items;
}
