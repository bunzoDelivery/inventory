package com.quickcommerce.product.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for the batch variant-group fetch endpoint.
 * The mobile app sends the groupIds it collected from a listing/search page
 * so variants for the bottom sheet can be pre-fetched in one round trip.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantGroupBatchRequest {

    @NotEmpty(message = "At least one group ID is required")
    @Size(max = 50, message = "Maximum 50 group IDs per request")
    private List<String> groupIds;
}
