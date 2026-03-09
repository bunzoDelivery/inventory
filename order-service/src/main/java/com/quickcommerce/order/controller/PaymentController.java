package com.quickcommerce.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.service.PaymentService;
import com.quickcommerce.order.payment.webhook.AirtelWebhookPayload;
import com.quickcommerce.order.payment.webhook.AirtelWebhookValidator;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AirtelWebhookValidator webhookValidator;
    private final ObjectMapper objectMapper;

    @Qualifier("payInitiationRateLimiter")
    private final RateLimiter payInitiationRateLimiter;

    // ─── Initiate Airtel STK Push ─────────────────────────────────────────────

    /**
     * Step 2 of the payment flow. Called immediately after createOrder returns PENDING_PAYMENT.
     *
     * Requires X-Customer-Id header. Before the centralized auth gateway is deployed this header
     * must be set by the caller. Once the gateway is live it will inject this header automatically
     * from the validated JWT — no change needed here.
     */
    @PostMapping("/api/v1/orders/{orderUuid}/pay")
    public Mono<ResponseEntity<PaymentStatusResponse>> initiatePayment(
            @PathVariable String orderUuid,
            @RequestHeader(value = "X-Customer-Id", required = true) String customerId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("Initiating payment for order={}", orderUuid);
        return paymentService.initiatePayment(orderUuid, customerId, request)
                .map(ResponseEntity::ok)
                .transformDeferred(RateLimiterOperator.of(payInitiationRateLimiter));
    }

    // ─── Airtel Webhook ───────────────────────────────────────────────────────

    /**
     * Airtel POSTs here after customer enters PIN.
     * CRITICAL: Always returns 200 OK — even on validation failure or processing error.
     * If we return non-200, Airtel will retry the webhook endlessly.
     */
    @PostMapping("/api/v1/webhooks/airtel")
    public Mono<ResponseEntity<Map<String, String>>> handleAirtelWebhook(
            @RequestBody String rawBody,
            ServerHttpRequest request) {

        return webhookValidator.validate(request, rawBody)
                .then(Mono.defer(() -> {
                    AirtelWebhookPayload payload;
                    try {
                        payload = parseWebhookPayload(rawBody);
                    } catch (Exception e) {
                        log.error("Failed to parse Airtel webhook body: {}", rawBody, e);
                        return Mono.just(ResponseEntity.ok(Map.of("status", "ACCEPTED")));
                    }

                    return paymentService.handleWebhook(payload, rawBody)
                            .thenReturn(ResponseEntity.ok(Map.of("status", "ACCEPTED")));
                }))
                .onErrorResume(e -> {
                    log.error("Airtel webhook processing error", e);
                    return Mono.just(ResponseEntity.ok(Map.of("status", "ACCEPTED")));
                });
    }

    // ─── Frontend Polling ─────────────────────────────────────────────────────

    /**
     * Frontend polls this every 3 seconds while waiting for the customer to enter PIN.
     *
     * Requires X-Customer-Id header (see comment on initiatePayment above).
     */
    @GetMapping("/api/v1/orders/{orderUuid}/payment-status")
    public Mono<ResponseEntity<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable String orderUuid,
            @RequestHeader(value = "X-Customer-Id", required = true) String customerId) {
        return paymentService.getPaymentStatus(orderUuid, customerId)
                .map(ResponseEntity::ok);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AirtelWebhookPayload parseWebhookPayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, AirtelWebhookPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook payload", e);
        }
    }
}
