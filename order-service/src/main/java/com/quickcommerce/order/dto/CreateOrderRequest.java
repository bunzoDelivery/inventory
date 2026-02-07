package com.quickcommerce.order.dto;

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
public class CreateOrderRequest {
    
    @NotNull
    private String storeId;
    
    @NotNull
    private Long customerId;
    
    @NotEmpty
    private List<OrderItemRequest> items;
    
    @NotNull
    private String paymentMethod; // COD, AIRTEL_MONEY

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotNull
        private String sku;
        
        @NotNull
        private Integer quantity;
    }
}
