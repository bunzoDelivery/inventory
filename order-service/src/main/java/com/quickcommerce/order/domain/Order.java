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

    // Logistic status (state machine enforced in service layer)
    private String status;

    // Money tracking
    private String paymentMethod;
    private String paymentStatus;

    private BigDecimal totalAmount;
    private BigDecimal deliveryFee;
    private String currency;

    // Delivery details
    private String deliveryAddress;
    private BigDecimal deliveryLat;
    private BigDecimal deliveryLng;
    private String deliveryPhone;
    private String deliveryNotes;

    private String cancelledReason;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private Long shippingAddressId;
    private String idempotencyKey;

    public OrderStatus orderStatus() {
        return OrderStatus.valueOf(this.status);
    }

    public PaymentMethod paymentMethodEnum() {
        return PaymentMethod.valueOf(this.paymentMethod);
    }

    public BigDecimal getGrandTotal() {
        BigDecimal fee = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        return totalAmount != null ? totalAmount.add(fee) : fee;
    }
}
