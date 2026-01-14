package com.quickcommerce.search.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    /**
     * Search query string
     */
    @NotBlank(message = "Query cannot be blank")
    private String query;

    /**
     * Store ID to filter results by
     */
    @NotNull(message = "Store ID is required")
    private Long storeId;

    /**
     * Maximum number of results to return (1-100)
     */
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 100, message = "Limit cannot exceed 100")
    private Integer limit = 20;
}
