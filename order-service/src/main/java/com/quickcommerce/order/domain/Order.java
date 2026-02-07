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
    private String storeId;
    
    private String status; // PENDING_PAYMENT, CONFIRMED, CANCELLED
    
    private String paymentMethod; // COD, AIRTEL_MONEY
    private String paymentStatus; // PENDING, PAID, COD_PENDING
    
    private BigDecimal totalAmount;
    private String currency; // Default ZMW

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private Long shippingAddressId;
}
