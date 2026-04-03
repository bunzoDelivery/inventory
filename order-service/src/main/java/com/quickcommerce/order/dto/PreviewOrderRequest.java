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

    @Valid
    private List<PreviewItemRequest> items;

    @Valid
    private List<PrintItemRequest> printItems;

    public boolean hasRegularItems() {
        return items != null && !items.isEmpty();
    }

    public boolean hasPrintItems() {
        return printItems != null && !printItems.isEmpty();
    }

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintItemRequest {

        @Size(max = 255, message = "Label too long")
        private String label;

        @NotNull(message = "Page count is required")
        @Min(value = 1, message = "Page count must be at least 1")
        @Max(value = 500, message = "Page count cannot exceed 500")
        private Integer pages;

        @NotNull(message = "Color mode is required")
        @Pattern(regexp = "BLACK_WHITE|COLOR", message = "Color mode must be BLACK_WHITE or COLOR")
        private String colorMode;

        @NotNull(message = "Sides is required")
        @Pattern(regexp = "SINGLE|DOUBLE", message = "Sides must be SINGLE or DOUBLE")
        private String sides;

        @Min(value = 1, message = "Copies must be at least 1")
        @Max(value = 100, message = "Copies cannot exceed 100")
        @Builder.Default
        private Integer copies = 1;
    }
}
