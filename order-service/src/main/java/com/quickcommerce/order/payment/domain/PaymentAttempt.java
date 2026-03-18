package com.quickcommerce.order.payment.domain;

import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.payment.gateway.GatewayName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Audit record for each mobile-money USSD push attempt.
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

    /** Gateway-assigned transaction / deposit ID — used to match incoming webhooks */
    private String gatewayRef;

    /** Which gateway adapter processed this attempt. Used by failsafe scheduler for routing. */
    private GatewayName gatewayUsed;

    /** Which mobile network was dialled (AIRTEL | MTN). */
    private MobileNetwork mobileNetwork;

    private PaymentAttemptStatus status;

    private String failureReason;

    private LocalDateTime initiatedAt;
    private LocalDateTime resolvedAt;

    /** Raw webhook JSON body — stored for reconciliation and debugging */
    private String rawWebhook;
}
