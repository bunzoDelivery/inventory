package com.quickcommerce.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("customer_orders")
public class Order {

    @Id
    private Long id;

    private String orderUuid;
    private String customerId;
    private Long storeId;
    
    private String status; // PENDING_PAYMENT, CONFIRMED, PACKING, DELIVERED, CANCELLED
    
    private String paymentMethod; // COD, AIRTEL_MONEY, MTN_MONEY
    private String paymentStatus; // PENDING, PAID, COD_PENDING
    
    private BigDecimal totalAmount;
    private String currency; // Default ZMW

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private Long shippingAddressId;

    private String idempotencyKey;
}
