package com.quickcommerce.order.payment.gateway;

import reactor.core.publisher.Mono;

/**
 * Port (abstraction) over any mobile-money payment provider.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@code AirtelDirectGateway}  — wraps the existing Airtel Money client (profile: airtel / mock-airtel)</li>
 *   <li>{@code PawaPayGateway}       — PawaPay aggregator, supports Airtel + MTN (profile: pawapay)</li>
 *   <li>{@code MockPawaPayGateway}   — dev/test stub (profile: mock-pawapay)</li>
 * </ul>
 *
 * <p>The active gateway for *new* payments is selected by
 * {@link PaymentGatewayRouter} using the {@code payment.active-gateway} config property.
 * The failsafe scheduler resolves the gateway by name from stored {@code gateway_used}
 * on each {@code payment_attempts} row.
 */
public interface PaymentGateway {

    GatewayName getGatewayName();

    /**
     * Initiates a USSD push to the customer's phone.
     *
     * @param request canonical payment request
     * @return response containing the provider-assigned {@code gatewayRef}
     */
    Mono<GatewayPaymentResponse> initiatePayment(GatewayPaymentRequest request);

    /**
     * Queries the provider for the current status of a transaction.
     * Used by {@link com.quickcommerce.order.payment.scheduler.GenericPaymentFailsafeScheduler}
     * to catch orders whose webhook was never received.
     *
     * @param gatewayRef the provider reference returned by {@link #initiatePayment}
     */
    Mono<GatewayStatusResponse> checkPaymentStatus(String gatewayRef);
}
