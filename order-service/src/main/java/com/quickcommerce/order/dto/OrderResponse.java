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
public class OrderResponse {
    private String orderId;
    private String status;
    private String message;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subTotal;
    }
}
