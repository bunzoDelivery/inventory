package com.quickcommerce.order.payment.service;

import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.domain.OrderEvent;
import com.quickcommerce.order.domain.OrderStatus;
import com.quickcommerce.order.domain.PaymentStatus;
import com.quickcommerce.order.exception.InvalidOrderStateException;
import com.quickcommerce.order.exception.OrderNotFoundException;
import com.quickcommerce.order.payment.client.AirtelMoneyClient;
import com.quickcommerce.order.payment.client.AirtelPushRequest;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
import com.quickcommerce.order.payment.webhook.AirtelWebhookPayload;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderRepository;
import com.quickcommerce.order.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final PaymentAttemptRepository paymentAttemptRepo;
    private final AirtelMoneyClient airtelClient;
    private final InventoryClient inventoryClient;
    private final NotificationClient notificationClient;
    private final TransactionalOperator transactionalOperator;

    @Value("${airtel.country:ZM}")
    private String country;

    @Value("${airtel.currency:ZMW}")
    private String currency;

    // ─── Initiate Payment ─────────────────────────────────────────────────────

    /**
     * Initiates an Airtel Money USSD STK push for an order in PENDING_PAYMENT status.
     * Called by: POST /api/v1/orders/{uuid}/pay
     *
     * @param customerId the authenticated customer ID (from X-Customer-Id header)
     */
    public Mono<PaymentStatusResponse> initiatePayment(String orderUuid, String customerId,
            InitiatePaymentRequest req) {
        log.info("Initiating Airtel payment for order={}, phone={}", orderUuid,
                PhoneUtils.maskPhone(req.getPaymentPhone()));

        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(order -> {

                    // Auth: verify the caller owns this order
                    if (!order.getCustomerId().equals(customerId)) {
                        return Mono.error(new InvalidOrderStateException(
                                "Order does not belong to this customer"));
                    }

                    // Guard: only PENDING_PAYMENT orders can be paid
                    if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
                        return Mono.error(new InvalidOrderStateException(
                                "Order " + orderUuid + " is in status " + order.getStatus() +
                                        " and cannot initiate payment. Expected: PENDING_PAYMENT"));
                    }

                    if (order.paymentMethodEnum().isCashOnDelivery()) {
                        return Mono.error(
                                new InvalidOrderStateException("COD orders cannot initiate Airtel payment"));
                    }

                    // Guard: reject if a push was already initiated for this order
                    return paymentAttemptRepo.findByOrderUuid(orderUuid)
                            .flatMap(existing -> Mono.<PaymentStatusResponse>error(
                                    new InvalidOrderStateException(
                                            "Payment already initiated for this order. " +
                                                    "Awaiting PIN confirmation.")))
                            .switchIfEmpty(Mono.defer(() -> proceedWithPush(order, req)));
                });
    }

    private Mono<PaymentStatusResponse> proceedWithPush(Order order, InitiatePaymentRequest req) {
        String orderUuid = order.getOrderUuid();

        order.setPaymentPhone(req.getPaymentPhone());

        AirtelPushRequest pushReq = AirtelPushRequest.builder()
                .msisdn(req.getPaymentPhone())
                .reference(orderUuid)
                .amount(order.getGrandTotal())
                .country(country)
                .currency(currency)
                .build();

        PaymentAttempt attempt = PaymentAttempt.builder()
                .orderUuid(orderUuid)
                .paymentPhone(req.getPaymentPhone())
                .status(PaymentAttemptStatus.INITIATED)
                .initiatedAt(LocalDateTime.now())
                .build();

        return paymentAttemptRepo.save(attempt)
                // Backstop for concurrent requests: both passed the findByOrderUuid check,
                // but only one INSERT can succeed on the UNIQUE(order_uuid) constraint.
                .onErrorResume(DataIntegrityViolationException.class, e -> {
                    log.warn("Duplicate payment attempt blocked by DB constraint for order={}", orderUuid);
                    return Mono.error(new InvalidOrderStateException(
                            "Payment already initiated for this order. Awaiting PIN confirmation."));
                })
                .flatMap(savedAttempt -> airtelClient.initiateUssdPush(pushReq)
                        .flatMap(pushResp -> {
                            if (pushResp.getAirtelTransactionId() == null
                                    || pushResp.getAirtelTransactionId().isBlank()) {
                                // Airtel returned a response with no transaction ID — treat as failure
                                savedAttempt.setStatus(PaymentAttemptStatus.FAILED);
                                savedAttempt.setFailureReason("Airtel returned no transaction ID");
                                savedAttempt.setResolvedAt(LocalDateTime.now());
                                return paymentAttemptRepo.save(savedAttempt)
                                        .then(Mono.error(new RuntimeException(
                                                "Airtel did not return a transaction ID. Please try COD or contact support.")));
                            }

                            order.setAirtelTransactionId(pushResp.getAirtelTransactionId());
                            savedAttempt.setAirtelRef(pushResp.getAirtelTransactionId());

                            return orderRepo.save(order)
                                    .then(paymentAttemptRepo.save(savedAttempt))
                                    .then(Mono.just(PaymentStatusResponse.builder()
                                            .orderUuid(orderUuid)
                                            .orderStatus(order.getStatus())
                                            .paymentStatus(order.getPaymentStatus())
                                            .paymentPhone(PhoneUtils.maskPhone(req.getPaymentPhone()))
                                            .pushStatus("PUSH_SENT")
                                            .message("Airtel Money prompt sent to "
                                                    + PhoneUtils.maskPhone(req.getPaymentPhone())
                                                    + ". Please enter your PIN.")
                                            .build()));
                        })
                        .onErrorResume(e -> {
                            if (e instanceof InvalidOrderStateException) return Mono.error(e);
                            // STK push failed (Airtel API down, invalid number, etc.)
                            log.error("Airtel STK push failed for order={}", orderUuid, e);
                            savedAttempt.setStatus(PaymentAttemptStatus.FAILED);
                            savedAttempt.setFailureReason("STK push failed: " + e.getMessage());
                            savedAttempt.setResolvedAt(LocalDateTime.now());
                            return paymentAttemptRepo.save(savedAttempt)
                                    .then(Mono.error(new RuntimeException(
                                            "Failed to initiate Airtel payment. Please try COD or contact support.",
                                            e)));
                        }));
    }

    // ─── Handle Webhook ───────────────────────────────────────────────────────

    /**
     * Processes Airtel's webhook callback after customer enters PIN.
     * Called by: POST /api/v1/webhooks/airtel
     *
     * Always returns Mono.empty() — the controller always sends 200 OK to Airtel
     * regardless of processing outcome, to prevent Airtel from retrying.
     */
    public Mono<Void> handleWebhook(AirtelWebhookPayload payload, String rawBody) {
        String transactionId = payload.getTransactionId();
        String statusCode = payload.getStatusCode();
        boolean isSuccess = payload.isSuccess();

        log.info("Airtel webhook received — txId={}, status={}", transactionId, statusCode);

        if (transactionId == null || transactionId.isBlank()) {
            log.warn("Airtel webhook missing transactionId — ignoring");
            return Mono.empty();
        }

        return paymentAttemptRepo.findByAirtelRef(transactionId)
                .flatMap(attempt -> orderRepo.findByOrderUuid(attempt.getOrderUuid())
                        .flatMap(order -> processWebhookForOrder(order, attempt, isSuccess, statusCode, rawBody))
                        // Emit a sentinel so switchIfEmpty below knows the attempt WAS found
                        // (processWebhookForOrder returns Mono<Void> which emits no element)
                        .thenReturn(Boolean.TRUE))
                // Fallback: attempt not yet persisted (webhook arrived before initiatePayment saves completed)
                .switchIfEmpty(Mono.defer(() -> orderRepo.findByAirtelTransactionId(transactionId)
                        .flatMap(order -> {
                            if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
                                log.info("Fallback webhook: order {} already in status {}. Ignoring.",
                                        order.getOrderUuid(), order.getStatus());
                                return Mono.just(Boolean.FALSE);
                            }
                            log.info("Fallback webhook path for order={}: no attempt record found, " +
                                    "processing directly from order", order.getOrderUuid());
                            PaymentAttempt synth = buildSyntheticAttempt(order, transactionId, rawBody);
                            return (isSuccess
                                    ? processPaymentSuccess(order, synth)
                                    : processPaymentFailure(order, synth, "Airtel reported: " + statusCode))
                                    .thenReturn(Boolean.FALSE);
                        })
                        .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                "No order or attempt found for Airtel txId={}. " +
                                        "Possible duplicate or stale webhook.", transactionId))
                                .thenReturn(Boolean.FALSE))))
                .then();
    }

    private Mono<Void> processWebhookForOrder(Order order, PaymentAttempt attempt,
            boolean isSuccess, String statusCode, String rawBody) {
        // Idempotency guard: don't re-process if already terminal
        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            log.info("Order {} already in status {}. Ignoring duplicate webhook.",
                    order.getOrderUuid(), order.getStatus());
            return Mono.empty();
        }

        attempt.setRawWebhook(rawBody);
        attempt.setResolvedAt(LocalDateTime.now());

        if (isSuccess) {
            return processPaymentSuccess(order, attempt);
        } else {
            return processPaymentFailure(order, attempt, "Airtel reported: " + statusCode);
        }
    }

    // ─── Payment Status (for polling) ─────────────────────────────────────────

    /**
     * Returns current payment status for frontend polling.
     * Called by: GET /api/v1/orders/{uuid}/payment-status
     */
    public Mono<PaymentStatusResponse> getPaymentStatus(String orderUuid, String customerId) {
        return orderRepo.findByOrderUuid(orderUuid)
                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                .flatMap(order -> {
                    // Auth: verify the caller owns this order
                    if (!order.getCustomerId().equals(customerId)) {
                        return Mono.error(new InvalidOrderStateException(
                                "Order does not belong to this customer"));
                    }

                    if (order.paymentMethodEnum().isCashOnDelivery()) {
                        return Mono.error(new InvalidOrderStateException(
                                "Payment status polling is not applicable for COD orders"));
                    }

                    String pushStatus = switch (OrderStatus.valueOf(order.getStatus())) {
                        case PENDING_PAYMENT ->
                            order.getAirtelTransactionId() != null ? "PUSH_SENT" : "AWAITING_PUSH";
                        case CONFIRMED -> "CONFIRMED";
                        case CANCELLED -> "FAILED";
                        default -> "UNKNOWN";
                    };

                    return Mono.just(PaymentStatusResponse.builder()
                            .orderUuid(orderUuid)
                            .orderStatus(order.getStatus())
                            .paymentStatus(order.getPaymentStatus())
                            .paymentPhone(PhoneUtils.maskPhone(order.getPaymentPhone()))
                            .pushStatus(pushStatus)
                            .message(pushStatusMessage(order.getStatus()))
                            .build());
                });
    }

    // ─── Internal: success / failure transitions ───────────────────────────────

    /**
     * Transitions order to CONFIRMED. Commits inventory. Fires notification.
     * Called by webhook handler and AirtelFailsafeScheduler.
     */
    public Mono<Void> processPaymentSuccess(Order order, PaymentAttempt attempt) {
        log.info("Payment SUCCESS for order={}", order.getOrderUuid());

        order.setStatus(OrderStatus.CONFIRMED.name());
        order.setPaymentStatus(PaymentStatus.PAID.name());
        attempt.setStatus(PaymentAttemptStatus.SUCCESS);

        if (attempt.getResolvedAt() == null) {
            attempt.setResolvedAt(LocalDateTime.now());
        }

        // Save order + attempt + event atomically, then confirm inventory outside the transaction
        return orderRepo.save(order)
                .flatMap(saved -> paymentAttemptRepo.save(attempt)
                        .then(orderEventRepo.save(
                                OrderEvent.paymentReceived(saved.getId(), saved.paymentMethodEnum())))
                        .as(transactionalOperator::transactional)
                        .thenReturn(saved))
                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                    // Another thread (webhook or failsafe) already processed this order — no-op
                    log.info("Optimistic lock conflict on order={}: already processed by concurrent handler",
                            order.getOrderUuid());
                    return Mono.empty();
                })
                .flatMap(saved -> inventoryClient.confirmReservation(saved.getOrderUuid())
                        .onErrorResume(e -> {
                            log.error("RECONCILE NEEDED: inventory confirmation failed for CONFIRMED order={}. " +
                                    "Manual reconciliation required.", saved.getOrderUuid(), e);
                            return Mono.empty();
                        })
                        .then(notificationClient.sendOrderConfirmedEvent(saved)
                                .onErrorResume(e -> {
                                    log.error("Notification failed for order={}", saved.getOrderUuid(), e);
                                    return Mono.empty();
                                })))
                .then();
    }

    /**
     * Transitions order to CANCELLED. Releases inventory reservation.
     * Called by webhook handler and AirtelFailsafeScheduler.
     */
    public Mono<Void> processPaymentFailure(Order order, PaymentAttempt attempt, String reason) {
        log.info("Payment FAILED for order={} — reason={}", order.getOrderUuid(), reason);
        OrderStatus previous = order.orderStatus();

        order.setStatus(OrderStatus.CANCELLED.name());
        order.setPaymentStatus(PaymentStatus.FAILED.name());
        order.setCancelledReason("PAYMENT_FAILED: " + reason);
        attempt.setStatus(PaymentAttemptStatus.FAILED);
        attempt.setFailureReason(reason);

        if (attempt.getResolvedAt() == null) {
            attempt.setResolvedAt(LocalDateTime.now());
        }

        return orderRepo.save(order)
                .flatMap(saved -> paymentAttemptRepo.save(attempt)
                        .then(orderEventRepo.save(
                                OrderEvent.cancelled(saved.getId(), previous,
                                        "PAYMENT_FAILED: " + reason, "AIRTEL_WEBHOOK")))
                        .as(transactionalOperator::transactional)
                        .thenReturn(saved))
                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                    log.info("Optimistic lock conflict on order={}: already processed by concurrent handler",
                            order.getOrderUuid());
                    return Mono.empty();
                })
                .flatMap(saved -> inventoryClient.cancelOrderReservations(saved.getOrderUuid())
                        .onErrorResume(e -> {
                            log.error("Failed to release stock for cancelled order={}", saved.getOrderUuid(), e);
                            return Mono.empty();
                        }))
                .then();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PaymentAttempt buildSyntheticAttempt(Order order, String transactionId, String rawBody) {
        return PaymentAttempt.builder()
                .orderUuid(order.getOrderUuid())
                .paymentPhone(order.getPaymentPhone())
                .airtelRef(transactionId)
                .status(PaymentAttemptStatus.INITIATED)
                .initiatedAt(order.getUpdatedAt())
                .rawWebhook(rawBody)
                .build();
    }

    private String pushStatusMessage(String status) {
        return switch (status) {
            case "PENDING_PAYMENT" -> "Waiting for Airtel PIN confirmation";
            case "CONFIRMED" -> "Payment confirmed. Your order is being prepared.";
            case "CANCELLED" -> "Payment was not completed. Please try again or switch to COD.";
            default -> "";
        };
    }
}
