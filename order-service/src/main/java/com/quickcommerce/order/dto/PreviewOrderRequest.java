package com.quickcommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
        @NotNull
        private String sku;

        @NotNull
        private Integer qty;
    }
}
