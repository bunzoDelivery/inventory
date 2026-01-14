package com.quickcommerce.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for inventory availability check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {

    /**
     * Store ID to check availability for
     */
    private Long storeId;

    /**
     * List of product IDs to check
     */
    private List<Long> productIds;
}
