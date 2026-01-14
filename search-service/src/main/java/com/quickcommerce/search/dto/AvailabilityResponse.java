package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO for inventory availability check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {

    /**
     * Store ID
     */
    private Long storeId;

    /**
     * Map of product ID to availability status
     * true = in stock, false = out of stock
     */
    private Map<Long, Boolean> availability;
}
