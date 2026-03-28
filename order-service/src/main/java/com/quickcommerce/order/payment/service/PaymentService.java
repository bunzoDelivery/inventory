package com.quickcommerce.order.payment.service;

import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.domain.OrderEvent;
import com.quickcommerce.order.domain.OrderStatus;
import com.quickcommerce.order.domain.PaymentMethod;
import com.quickcommerce.order.domain.PaymentStatus;
import com.quickcommerce.order.exception.InvalidOrderStateException;
import com.quickcommerce.order.exception.OrderNotFoundException;
import com.quickcommerce.order.exception.PaymentGatewayException;
import com.quickcommerce.order.payment.config.PaymentMethodsConfig;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.dto.AvailablePaymentMethodsResponse;
import com.quickcommerce.order.payment.dto.AvailablePaymentMethodsResponse.PaymentMethodEntry;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.gateway.GatewayPaymentRequest;
import com.quickcommerce.order.payment.gateway.PaymentGatewayRouter;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderRepository;
import com.quickcommerce.order.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

        private final OrderRepository orderRepo;
        private final OrderEventRepository orderEventRepo;
        private final PaymentAttemptRepository paymentAttemptRepo;
        private final PaymentGatewayRouter gatewayRouter;
        private final InventoryClient inventoryClient;
        private final NotificationClient notificationClient;
        private final TransactionalOperator transactionalOperator;
        private final PaymentMethodsConfig paymentMethodsConfig;

        // ─── Available Payment Methods (checkout discovery) ───────────────────────

        /**
         * Returns all payment methods with their current enabled/disabled status.
         * Called by: GET /api/v1/payment-methods
         *
         * <p>
         * Enum-driven and modular — iterates over all defined {@link PaymentMethod}
         * values.
         * Labels are stored in the enum, and enablement status is managed via
         * {@link PaymentMethodsConfig}.
         */
        public AvailablePaymentMethodsResponse getAvailablePaymentMethods() {
                List<PaymentMethodEntry> entries = Arrays.stream(PaymentMethod.values())
                                .map(method -> PaymentMethodEntry.builder()
                                                .code(method.name())
                                                .label(method.getLabel())
                                                .enabled(paymentMethodsConfig.isEnabled(method))
                                                .build())
                                .toList();
                return AvailablePaymentMethodsResponse.builder().methods(entries).build();
        }

        // ─── Initiate Payment ─────────────────────────────────────────────────────

        /**
         * Initiates a USSD push for an order in PENDING_PAYMENT status.
         * The active gateway is resolved from config via {@link PaymentGatewayRouter}.
         * Called by: POST /api/v1/orders/{uuid}/pay
         */
        public Mono<PaymentStatusResponse> initiatePayment(String orderUuid, String customerId,
                        InitiatePaymentRequest req) {
                log.info("Initiating payment for order={}, phone={}, network={}, gateway={}",
                                orderUuid, PhoneUtils.maskPhone(req.getPaymentPhone()),
                                req.getMobileNetwork(), gatewayRouter.resolve().getGatewayName());

                return orderRepo.findByOrderUuid(orderUuid)
                                .switchIfEmpty(Mono.error(new OrderNotFoundException("Order not found: " + orderUuid)))
                                .flatMap(order -> {

                                        if (!order.getCustomerId().equals(customerId)) {
                                                return Mono.error(new InvalidOrderStateException(
                                                                "Order does not belong to this customer"));
                                        }

                                        if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
                                                return Mono.error(new InvalidOrderStateException(
                                                                "Order " + orderUuid + " is in status "
                                                                                + order.getStatus() +
                                                                                " and cannot initiate payment. Expected: PENDING_PAYMENT"));
                                        }

                                        if (order.paymentMethodEnum().isCashOnDelivery()) {
                                                return Mono.error(new InvalidOrderStateException(
                                                                "COD orders cannot initiate mobile money payment"));
                                        }

                                        String networkMismatch = validatePhoneNetwork(
                                                        req.getPaymentPhone(), req.getMobileNetwork());
                                        if (networkMismatch != null) {
                                                return Mono.error(new InvalidOrderStateException(networkMismatch));
                                        }

                                        return paymentAttemptRepo.findByOrderUuid(orderUuid)
                                                        .flatMap(existing -> Mono.<PaymentStatusResponse>error(
                                                                        new InvalidOrderStateException(
                                                                                        "Payment already initiated for this order. "
                                                                                                        +
                                                                                                        "Awaiting PIN confirmation.")))
                                                        .switchIfEmpty(Mono.defer(() -> proceedWithPush(order, req)));
                                });
        }

        private Mono<PaymentStatusResponse> proceedWithPush(Order order, InitiatePaymentRequest req) {
                String orderUuid = order.getOrderUuid();
                var gateway = gatewayRouter.resolve();

                order.setPaymentPhone(req.getPaymentPhone());

                GatewayPaymentRequest gatewayReq = GatewayPaymentRequest.builder()
                                .orderUuid(orderUuid)
                                .msisdn(req.getPaymentPhone())
                                .amount(order.getGrandTotal())
                                .currency("ZMW")
                                .mobileNetwork(req.getMobileNetwork())
                                .build();

                // For PawaPay the depositId equals our orderUuid (we choose it) — pre-populate
                // so
                // the failsafe can find this attempt even if the post-gateway saves fail.
                // For Airtel Direct the ref is assigned by Airtel and only known after the push
                // call.
                String preKnownRef = gateway
                                .getGatewayName() == com.quickcommerce.order.payment.gateway.GatewayName.PAWAPAY
                                                ? orderUuid
                                                : null;

                PaymentAttempt attempt = PaymentAttempt.builder()
                                .orderUuid(orderUuid)
                                .paymentPhone(req.getPaymentPhone())
                                .gatewayUsed(gateway.getGatewayName())
                                .mobileNetwork(req.getMobileNetwork())
                                .gatewayRef(preKnownRef)
                                .status(PaymentAttemptStatus.INITIATED)
                                .initiatedAt(LocalDateTime.now())
                                .build();

                return paymentAttemptRepo.save(attempt)
                                // DB UNIQUE(order_uuid) backstop: blocks concurrent duplicate pushes
                                .onErrorResume(DataIntegrityViolationException.class, e -> {
                                        log.warn("Duplicate payment attempt blocked by DB constraint for order={}",
                                                        orderUuid);
                                        return Mono.error(new InvalidOrderStateException(
                                                        "Payment already initiated for this order. Awaiting PIN confirmation."));
                                })
                                .flatMap(savedAttempt -> gateway.initiatePayment(gatewayReq)
                                                .flatMap(gatewayResp -> {
                                                        order.setGatewayTransactionId(gatewayResp.getGatewayRef());
                                                        savedAttempt.setGatewayRef(gatewayResp.getGatewayRef());

                                                        return orderRepo.save(order)
                                                                        .then(paymentAttemptRepo.save(savedAttempt))
                                                                        .then(Mono.just(PaymentStatusResponse.builder()
                                                                                        .orderUuid(orderUuid)
                                                                                        .orderStatus(order.getStatus())
                                                                                        .paymentStatus(order
                                                                                                        .getPaymentStatus())
                                                                                        .paymentPhone(PhoneUtils
                                                                                                        .maskPhone(req.getPaymentPhone()))
                                                                                        .mobileNetwork(req
                                                                                                        .getMobileNetwork()
                                                                                                        .name())
                                                                                        .pushStatus("PUSH_SENT")
                                                                                        .message("Payment prompt sent to "
                                                                                                        + PhoneUtils.maskPhone(
                                                                                                                        req.getPaymentPhone())
                                                                                                        + ". Please enter your PIN.")
                                                                                        .build()));
                                                })
                                                .onErrorResume(e -> {
                                                        if (e instanceof InvalidOrderStateException)
                                                                return Mono.error(e);
                                                        log.error("Payment push failed for order={} via gateway={}",
                                                                        orderUuid, gateway.getGatewayName(), e);
                                                        savedAttempt.setStatus(PaymentAttemptStatus.FAILED);
                                                        savedAttempt.setFailureReason("Push failed: " + e.getMessage());
                                                        savedAttempt.setResolvedAt(LocalDateTime.now());
                                                        return paymentAttemptRepo.save(savedAttempt)
                                                                        .then(Mono.error(
                                                                                        e instanceof PaymentGatewayException
                                                                                                        ? e
                                                                                                        : new PaymentGatewayException(
                                                                                                                        "Failed to initiate payment. Please try COD or contact support.",
                                                                                                                        e)));
                                                }));
        }

        // ─── Webhook / Result Processing ──────────────────────────────────────────

        /**
         * Generic entry point for all payment provider webhooks.
         * Both Airtel and PawaPay controllers parse their vendor formats and call this.
         * Always returns {@code Mono.empty()} — the controller sends 200 OK regardless.
         *
         * @param gatewayRef the provider's transaction/deposit ID
         * @param isSuccess  true if the provider reported a successful payment
         * @param statusCode raw provider status string (for audit / cancel reason)
         * @param rawBody    raw webhook JSON body (stored for reconciliation)
         * @param actor      who triggered this result, e.g. "PAWAPAY_WEBHOOK"
         */
        public Mono<Void> processPaymentResult(String gatewayRef, boolean isSuccess,
                        String statusCode, String rawBody, String actor) {

                log.info("Processing payment result — gatewayRef={}, success={}, status={}, actor={}",
                                gatewayRef, isSuccess, statusCode, actor);

                if (gatewayRef == null || gatewayRef.isBlank()) {
                        log.warn("Payment result missing gatewayRef — ignoring");
                        return Mono.empty();
                }

                return paymentAttemptRepo.findByGatewayRef(gatewayRef)
                                .flatMap(attempt -> orderRepo.findByOrderUuid(attempt.getOrderUuid())
                                                .flatMap(order -> processResultForOrder(order, attempt, isSuccess,
                                                                statusCode, rawBody, actor))
                                                .thenReturn(Boolean.TRUE))
                                // Fallback: attempt not yet committed when webhook arrived (race condition)
                                .switchIfEmpty(Mono.defer(() -> orderRepo.findByGatewayTransactionId(gatewayRef)
                                                .flatMap(order -> {
                                                        if (!OrderStatus.PENDING_PAYMENT.name()
                                                                        .equals(order.getStatus())) {
                                                                log.info("Fallback webhook: order {} already {}. Ignoring.",
                                                                                order.getOrderUuid(),
                                                                                order.getStatus());
                                                                return Mono.just(Boolean.FALSE);
                                                        }
                                                        log.info("Fallback webhook path for order={}: no attempt found, "
                                                                        +
                                                                        "processing directly from order",
                                                                        order.getOrderUuid());
                                                        PaymentAttempt synth = buildSyntheticAttempt(order, gatewayRef,
                                                                        rawBody);
                                                        return (isSuccess
                                                                        ? processPaymentSuccess(order, synth)
                                                                        : processPaymentFailure(order, synth,
                                                                                        "Provider reported: "
                                                                                                        + statusCode))
                                                                        .thenReturn(Boolean.FALSE);
                                                })
                                                .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                                                "No order or attempt found for gatewayRef={}. " +
                                                                                "Possible duplicate or stale webhook.",
                                                                gatewayRef))
                                                                .thenReturn(Boolean.FALSE))))
                                .then();
        }

        private Mono<Void> processResultForOrder(Order order, PaymentAttempt attempt,
                        boolean isSuccess, String statusCode, String rawBody, String actor) {
                if (!OrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
                        log.info("Order {} already {}. Ignoring duplicate webhook from {}.",
                                        order.getOrderUuid(), order.getStatus(), actor);
                        return Mono.empty();
                }

                attempt.setRawWebhook(rawBody);
                attempt.setResolvedAt(LocalDateTime.now());

                return isSuccess
                                ? processPaymentSuccess(order, attempt)
                                : processPaymentFailure(order, attempt, "Provider reported: " + statusCode);
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
                                                        order.getGatewayTransactionId() != null ? "PUSH_SENT"
                                                                        : "AWAITING_PUSH";
                                                case CONFIRMED -> "CONFIRMED";
                                                case CANCELLED -> "FAILED";
                                                // Post-payment statuses — payment was confirmed
                                                default -> "CONFIRMED";
                                        };

                                        return paymentAttemptRepo.findByOrderUuid(orderUuid)
                                                        .map(attempt -> PaymentStatusResponse.builder()
                                                                        .orderUuid(orderUuid)
                                                                        .orderStatus(order.getStatus())
                                                                        .paymentStatus(order.getPaymentStatus())
                                                                        .paymentPhone(PhoneUtils.maskPhone(
                                                                                        order.getPaymentPhone()))
                                                                        .mobileNetwork(attempt
                                                                                        .getMobileNetwork() != null
                                                                                                        ? attempt.getMobileNetwork()
                                                                                                                        .name()
                                                                                                        : null)
                                                                        .pushStatus(pushStatus)
                                                                        .message(pushStatusMessage(order.getStatus()))
                                                                        .build())
                                                        .defaultIfEmpty(PaymentStatusResponse.builder()
                                                                        .orderUuid(orderUuid)
                                                                        .orderStatus(order.getStatus())
                                                                        .paymentStatus(order.getPaymentStatus())
                                                                        .paymentPhone(PhoneUtils.maskPhone(
                                                                                        order.getPaymentPhone()))
                                                                        .pushStatus(pushStatus)
                                                                        .message(pushStatusMessage(order.getStatus()))
                                                                        .build());
                                });
        }

        // ─── Internal: success / failure transitions ───────────────────────────────

        /**
         * Transitions order to CONFIRMED. Commits inventory. Fires notification.
         * Called by webhook handler and GenericPaymentFailsafeScheduler.
         */
        public Mono<Void> processPaymentSuccess(Order order, PaymentAttempt attempt) {
                log.info("Payment SUCCESS for order={}", order.getOrderUuid());

                order.setStatus(OrderStatus.CONFIRMED.name());
                order.setPaymentStatus(PaymentStatus.PAID.name());
                attempt.setStatus(PaymentAttemptStatus.SUCCESS);

                if (attempt.getResolvedAt() == null) {
                        attempt.setResolvedAt(LocalDateTime.now());
                }

                return orderRepo.save(order)
                                .flatMap(saved -> paymentAttemptRepo.save(attempt)
                                                .then(orderEventRepo.save(
                                                                OrderEvent.paymentReceived(saved.getId(),
                                                                                saved.paymentMethodEnum())))
                                                .thenReturn(saved))
                                .as(transactionalOperator::transactional)
                                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                                        log.info("Optimistic lock conflict on order={}: already processed by concurrent handler",
                                                        order.getOrderUuid());
                                        return Mono.empty();
                                })
                                .flatMap(saved -> inventoryClient.confirmReservation(saved.getOrderUuid())
                                                .onErrorResume(e -> {
                                                        log.error("RECONCILE NEEDED: inventory confirmation failed for CONFIRMED order={}. "
                                                                        +
                                                                        "Manual reconciliation required.",
                                                                        saved.getOrderUuid(), e);
                                                        return Mono.empty();
                                                })
                                                .then(notificationClient.sendOrderConfirmedEvent(saved)
                                                                .onErrorResume(e -> {
                                                                        log.error("Notification failed for order={}",
                                                                                        saved.getOrderUuid(), e);
                                                                        return Mono.empty();
                                                                })))
                                .then();
        }

        /**
         * Transitions order to CANCELLED. Releases inventory reservation.
         * Called by webhook handler and GenericPaymentFailsafeScheduler.
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

                // Derive actor from the gateway that processed this attempt
                String actor = attempt.getGatewayUsed() != null
                                ? attempt.getGatewayUsed().name() + "_WEBHOOK"
                                : "SYSTEM";

                return orderRepo.save(order)
                                .flatMap(saved -> paymentAttemptRepo.save(attempt)
                                                .then(orderEventRepo.save(
                                                                OrderEvent.cancelled(saved.getId(), previous,
                                                                                "PAYMENT_FAILED: " + reason, actor)))
                                                .thenReturn(saved))
                                .as(transactionalOperator::transactional)
                                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                                        log.info("Optimistic lock conflict on order={}: already processed by concurrent handler",
                                                        order.getOrderUuid());
                                        return Mono.empty();
                                })
                                .flatMap(saved -> inventoryClient.cancelOrderReservations(saved.getOrderUuid())
                                                .onErrorResume(e -> {
                                                        log.error("Failed to release stock for cancelled order={}",
                                                                        saved.getOrderUuid(), e);
                                                        return Mono.empty();
                                                }))
                                .then();
        }

        // ─── Helpers ──────────────────────────────────────────────────────────────

        private PaymentAttempt buildSyntheticAttempt(Order order, String gatewayRef, String rawBody) {
                // gatewayUsed is unknown in this fallback path — derive from active gateway as
                // best-effort.
                // This only happens when a webhook arrives before the attempt row is committed
                // (rare race).
                var gateway = gatewayRouter.resolve();
                return PaymentAttempt.builder()
                                .orderUuid(order.getOrderUuid())
                                .paymentPhone(order.getPaymentPhone())
                                .gatewayRef(gatewayRef)
                                .gatewayUsed(gateway.getGatewayName())
                                .status(PaymentAttemptStatus.INITIATED)
                                .initiatedAt(order.getUpdatedAt())
                                .rawWebhook(rawBody)
                                .build();
        }

        /**
         * Validates that the submitted mobileNetwork matches the phone number prefix.
         * Zambia: Airtel=097x/077x, MTN=096x/076x, Zamtel=095x
         * Indian numbers (for dev/test) skip this check.
         * Returns an error message string if mismatched, null if valid.
         */
        private String validatePhoneNetwork(String phone, com.quickcommerce.order.domain.MobileNetwork network) {
                if (phone == null || network == null)
                        return null;
                String digits = phone.replaceAll("\\D", "");
                // Strip country code to get local prefix
                String local = digits.startsWith("260") ? digits.substring(3)
                                : digits.startsWith("0") ? digits.substring(1)
                                                : digits;
                // Indian numbers (for dev/test) — skip network check
                if (local.length() == 10 && local.charAt(0) >= '6')
                        return null;
                if (local.length() < 2)
                        return null;
                String prefix2 = local.substring(0, 2);
                boolean isAirtelPrefix = prefix2.equals("97") || prefix2.equals("77");
                boolean isMtnPrefix = prefix2.equals("96") || prefix2.equals("76");
                return switch (network) {
                        case AIRTEL -> isMtnPrefix
                                        ? "Phone number " + PhoneUtils.maskPhone(phone)
                                                        + " appears to be an MTN number. Select MTN as the network."
                                        : null;
                        case MTN -> isAirtelPrefix
                                        ? "Phone number " + PhoneUtils.maskPhone(phone)
                                                        + " appears to be an Airtel number. Select AIRTEL as the network."
                                        : null;
                };
        }

        private String pushStatusMessage(String status) {
                return switch (status) {
                        case "PENDING_PAYMENT" -> "Waiting for payment PIN confirmation";
                        case "CONFIRMED" -> "Payment confirmed. Your order is being prepared.";
                        case "CANCELLED" -> "Payment was not completed. Please try again or switch to COD.";
                        default -> "";
                };
        }
}
