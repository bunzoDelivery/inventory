package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewOrderResponse {

    private Long storeId;
    private OrderSummary summary;
    private List<RegularItemResponse> regularItems;
    private List<PrintItemResponse> printItems;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSummary {
        private BigDecimal regularItemsTotal;
        private BigDecimal printItemsTotal;
        private BigDecimal grandTotal;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegularItemResponse {
        private String sku;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal subTotal;
        private Integer availableQuantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintItemResponse {
        private String label;
        private Integer pages;
        private String colorMode;
        private String sides;
        private Integer copies;
        private BigDecimal basePricePerPage;
        private BigDecimal sideMultiplier;
        private BigDecimal subTotal;
    }
}
