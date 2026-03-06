package com.quickcommerce.order.payment.client;

import reactor.core.publisher.Mono;

/**
 * Abstraction over the Airtel Money API.
 * Dev: MockAirtelMoneyClient (profile=mock-airtel)
 * Prod: HttpAirtelMoneyClient (profile=airtel)
 */
public interface AirtelMoneyClient {

    /**
     * Initiates a USSD STK push to the customer's phone.
     * The orderUuid is passed as the transaction reference for idempotency.
     */
    Mono<AirtelPushResponse> initiateUssdPush(AirtelPushRequest request);

    /**
     * Queries Airtel for the current status of a transaction.
     * Used by the failsafe scheduler to catch missed webhooks.
     */
    Mono<AirtelStatusResponse> checkPaymentStatus(String airtelTransactionId);
}
