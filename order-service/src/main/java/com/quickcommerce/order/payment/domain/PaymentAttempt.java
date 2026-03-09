package com.quickcommerce.order.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Audit record for each Airtel STK push attempt.
 * One Order can have at most one PaymentAttempt in MVP
 * (retry creates a new order, not a new attempt on the same order).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_attempts")
public class PaymentAttempt {

    @Id
    private Long id;

    private String orderUuid;
    private String paymentPhone;

    /** Airtel-assigned transaction ID — used to match incoming webhooks */
    private String airtelRef;

    private PaymentAttemptStatus status;

    private String failureReason;

    private LocalDateTime initiatedAt;
    private LocalDateTime resolvedAt;

    /** Raw webhook JSON body — stored for reconciliation and debugging */
    private String rawWebhook;
}
