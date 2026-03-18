package com.quickcommerce.order.payment.gateway;

/**
 * Identifies which payment gateway adapter processed (or will process) a transaction.
 * Stored on {@code payment_attempts.gateway_used} so the failsafe scheduler can route
 * status-check calls back to the correct provider even after switching active gateways.
 */
public enum GatewayName {
    PAWAPAY,
    AIRTEL_DIRECT
}
