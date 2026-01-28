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
     * Page number (1-indexed, default 1)
     */
    @Min(value = 1, message = "Page must be at least 1")
    @Builder.Default
    private Integer page = 1;

    /**
     * Number of results per page (max 100, default 20)
     */
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    @Builder.Default
    private Integer pageSize = 20;

    /**
     * @deprecated Use pageSize instead
     */
    @Deprecated
    private Integer limit; // Retain for backward compat, mapped to pageSize in service
}
