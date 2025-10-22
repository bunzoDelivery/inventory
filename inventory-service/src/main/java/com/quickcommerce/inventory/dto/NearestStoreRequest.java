package com.quickcommerce.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for finding nearest store with inventory
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearestStoreRequest {

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    @NotEmpty(message = "At least one SKU is required")
    private List<String> skus;
}
