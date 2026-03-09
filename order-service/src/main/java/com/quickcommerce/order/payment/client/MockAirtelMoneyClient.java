package com.quickcommerce.order.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Mock implementation of AirtelMoneyClient for local development.
 * Active when Spring profile includes "mock-airtel" (the default dev profile).
 *
 * Behaviour:
 * - initiateUssdPush → returns a fake airtelTransactionId immediately
 * (simulates push sent)
 * - checkPaymentStatus → returns "TS" (success) to simulate a customer who paid
 *
 * To test a failure scenario, POST the webhook manually with status "TF".
 */
@Slf4j
@Component
@Profile("mock-airtel")
public class MockAirtelMoneyClient implements AirtelMoneyClient {

    @Override
    public Mono<AirtelPushResponse> initiateUssdPush(AirtelPushRequest request) {
        String fakeAirtelRef = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("[MOCK-AIRTEL] STK push initiated — msisdn={}, reference={}, amount={} ZMW → fakeRef={}",
                request.getMsisdn(), request.getReference(), request.getAmount(), fakeAirtelRef);

        return Mono.just(AirtelPushResponse.builder()
                .airtelTransactionId(fakeAirtelRef)
                .status("DP_INITIATED")
                .message("Mock STK push initiated successfully")
                .build());
    }

    @Override
    public Mono<AirtelStatusResponse> checkPaymentStatus(String airtelTransactionId) {
        log.info("[MOCK-AIRTEL] Status check for transactionId={} → returning TS (success)", airtelTransactionId);

        return Mono.just(AirtelStatusResponse.builder()
                .airtelTransactionId(airtelTransactionId)
                .status("TS")
                .message("Mock: transaction successful")
                .build());
    }
}
