package com.quickcommerce.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quickcommerce.order.payment.dto.AvailablePaymentMethodsResponse;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.gateway.pawapay.PawaPayWebhookPayload;
import com.quickcommerce.order.payment.gateway.pawapay.PawaPayWebhookValidator;
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
    private final AirtelWebhookValidator airtelWebhookValidator;
    private final PawaPayWebhookValidator pawaPayWebhookValidator;
    private final ObjectMapper objectMapper;

    @Qualifier("payInitiationRateLimiter")
    private final RateLimiter payInitiationRateLimiter;

    // ─── Initiate Payment (generic — active gateway is resolved by PaymentService)
    // ──

    /**
     * Step 2 of the payment flow. Called immediately after createOrder returns
     * PENDING_PAYMENT.
     * The active payment gateway is resolved from config — no frontend changes
     * needed
     * when switching between PawaPay and Airtel Direct.
     */
    @PostMapping("/api/v1/orders/{orderUuid}/pay")
    public Mono<ResponseEntity<PaymentStatusResponse>> initiatePayment(
            @PathVariable String orderUuid,
            @RequestHeader(value = "X-Customer-Id") String customerId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        log.info("Initiating payment for order={}", orderUuid);
        return paymentService.initiatePayment(orderUuid, customerId, request)
                .map(ResponseEntity::ok)
                .transformDeferred(RateLimiterOperator.of(payInitiationRateLimiter));
    }

    // ─── Airtel Webhook ───────────────────────────────────────────────────────

    /**
     * Airtel POSTs here after customer enters PIN.
     * CRITICAL: Always returns 200 OK — even on validation failure or processing
     * error.
     * If we return non-200, Airtel will retry the webhook endlessly.
     */
    @PostMapping("/api/v1/webhooks/airtel")
    public Mono<ResponseEntity<Map<String, String>>> handleAirtelWebhook(
            @RequestBody String rawBody,
            ServerHttpRequest request) {

        return airtelWebhookValidator.validate(request, rawBody)
                .then(Mono.fromCallable(() -> objectMapper.readValue(rawBody, AirtelWebhookPayload.class)))
                .flatMap(payload -> paymentService.processPaymentResult(
                        payload.getTransactionId(),
                        payload.isSuccess(),
                        payload.getStatusCode(),
                        rawBody,
                        "AIRTEL_WEBHOOK"))
                .thenReturn(ResponseEntity.ok(Map.of("status", "ACCEPTED")))
                .onErrorResume(e -> {
                    log.error("Airtel webhook processing error — body={}", rawBody, e);
                    return Mono.just(ResponseEntity.ok(Map.of("status", "ACCEPTED")));
                });
    }

    // ─── PawaPay Webhook ──────────────────────────────────────────────────────

    /**
     * PawaPay POSTs here after the customer enters their PIN (or the request
     * expires).
     * CRITICAL: Always returns 200 OK — even on validation failure or processing
     * error.
     * If we return non-200, PawaPay will retry the webhook endlessly.
     *
     * PawaPay uses our own {@code orderUuid} as the {@code depositId}, so no extra
     * lookup is needed to correlate the webhook to an order.
     */
    @PostMapping("/api/v1/webhooks/pawapay")
    public Mono<ResponseEntity<Map<String, String>>> handlePawaPayWebhook(
            @RequestBody String rawBody,
            ServerHttpRequest request) {

        return pawaPayWebhookValidator.validate(request, rawBody)
                .then(Mono.fromCallable(() -> objectMapper.readValue(rawBody, PawaPayWebhookPayload.class)))
                .flatMap(payload -> {
                    if (payload.isDuplicate()) {
                        log.info("PawaPay DUPLICATE_IGNORED webhook for depositId={} — no-op",
                                payload.getDepositId());
                        return Mono.empty();
                    }
                    return paymentService.processPaymentResult(
                            payload.getDepositId(),
                            payload.isSuccess(),
                            payload.getStatus(),
                            rawBody,
                            "PAWAPAY_WEBHOOK");
                })
                .thenReturn(ResponseEntity.ok(Map.of("status", "ACCEPTED")))
                .onErrorResume(e -> {
                    log.error("PawaPay webhook processing error — body={}", rawBody, e);
                    return Mono.just(ResponseEntity.ok(Map.of("status", "ACCEPTED")));
                });
    }

    // ─── Available Payment Methods (checkout discovery) ───────────────────────

    /**
     * Returns all payment methods with their current enabled/disabled status.
     * The UI calls this on the checkout page to decide which options to show.
     * No auth required — this is public discovery data, not customer-specific.
     * Availability is driven entirely by env vars (see PaymentMethodsConfig).
     */
    @GetMapping("/api/v1/payment-methods")
    public Mono<ResponseEntity<AvailablePaymentMethodsResponse>> getAvailablePaymentMethods() {
        return Mono.fromSupplier(paymentService::getAvailablePaymentMethods)
                .map(ResponseEntity::ok);
    }

    // ─── Frontend Polling ─────────────────────────────────────────────────────

    /**
     * Frontend polls this every 3 seconds while waiting for the customer to enter
     * PIN.
     */
    @GetMapping("/api/v1/orders/{orderUuid}/payment-status")
    public Mono<ResponseEntity<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable String orderUuid,
            @RequestHeader(value = "X-Customer-Id") String customerId) {
        return paymentService.getPaymentStatus(orderUuid, customerId)
                .map(ResponseEntity::ok);
    }
}
