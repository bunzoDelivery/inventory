package com.quickcommerce.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private String orderId;
    private String status;
    private String paymentMethod;
    private String paymentStatus;
    private String message;

    private BigDecimal itemsTotal;
    private BigDecimal deliveryFee;
    private BigDecimal grandTotal;
    private String currency;

    private DeliveryInfo delivery;
    private List<OrderItemResponse> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private String address;
        private Double latitude;
        private Double longitude;
        private String phone;
        private String notes;
    }
}
