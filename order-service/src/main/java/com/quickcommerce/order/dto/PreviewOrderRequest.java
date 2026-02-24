package com.quickcommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewOrderRequest {

    @NotNull(message = "Store ID is required")
    private Long storeId;

    @NotEmpty(message = "Items list cannot be empty")
    @Valid
    private List<PreviewItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewItemRequest {

        @NotNull(message = "SKU is required")
        @NotBlank(message = "SKU cannot be blank")
        private String sku;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Cannot request more than 100 units of a single item")
        private Integer qty;
    }
}
