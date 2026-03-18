package com.quickcommerce.order.payment.gateway;

/**
 * Normalised outcome returned by {@link PaymentGateway#checkPaymentStatus}.
 * Each gateway maps its own status codes to one of these three states.
 */
public enum GatewayStatusOutcome {
    /** Payment confirmed by the provider. */
    SUCCESS,
    /** Payment failed, was abandoned, or expired. */
    FAILED,
    /** Payment is still in progress — leave it for the next scheduler run. */
    PENDING
}
