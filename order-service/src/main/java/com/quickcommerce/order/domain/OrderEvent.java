package com.quickcommerce.order.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("order_events")
public class OrderEvent {

    @Id
    private Long id;

    private Long orderId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String actorId;
    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;

    public static OrderEvent of(Long orderId, String eventType,
                                OrderStatus from, OrderStatus to, String actorId) {
        return OrderEvent.builder()
                .orderId(orderId)
                .eventType(eventType)
                .fromStatus(from != null ? from.name() : null)
                .toStatus(to != null ? to.name() : null)
                .actorId(actorId)
                .build();
    }

    public static OrderEvent created(Long orderId, OrderStatus initialStatus) {
        return of(orderId, "ORDER_CREATED", null, initialStatus, "SYSTEM");
    }

    public static OrderEvent statusChanged(Long orderId, OrderStatus from, OrderStatus to, String actorId) {
        return of(orderId, "STATUS_CHANGED", from, to, actorId);
    }

    public static OrderEvent paymentReceived(Long orderId, PaymentMethod method) {
        return OrderEvent.builder()
                .orderId(orderId)
                .eventType("PAYMENT_RECEIVED")
                .toStatus(OrderStatus.CONFIRMED.name())
                .actorId(method.name())
                .build();
    }

    public static OrderEvent cancelled(Long orderId, OrderStatus from, String reason, String actorId) {
        return OrderEvent.builder()
                .orderId(orderId)
                .eventType("ORDER_CANCELLED")
                .fromStatus(from.name())
                .toStatus(OrderStatus.CANCELLED.name())
                .actorId(actorId)
                .notes(reason)
                .build();
    }
}
