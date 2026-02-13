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
    private BigDecimal totalAmount;
    private List<PreviewItemResponse> items;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewItemResponse {
        private String sku;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal subTotal;
        private Boolean available;
    }
}
