package com.quickcommerce.order.payment.gateway;

import lombok.Builder;
import lombok.Value;

/**
 * Canonical status-check response returned by {@link PaymentGateway#checkPaymentStatus}.
 */
@Value
@Builder
public class GatewayStatusResponse {

    String gatewayRef;
    GatewayStatusOutcome outcome;

    /** Raw provider status string (e.g. "TS", "COMPLETED") — stored for audit / debugging. */
    String rawStatus;
}
