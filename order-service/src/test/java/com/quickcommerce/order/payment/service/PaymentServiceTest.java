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
import com.quickcommerce.order.payment.client.AirtelPushResponse;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
import com.quickcommerce.order.payment.webhook.AirtelWebhookPayload;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private OrderEventRepository orderEventRepo;
    @Mock private PaymentAttemptRepository paymentAttemptRepo;
    @Mock private AirtelMoneyClient airtelClient;
    @Mock private InventoryClient inventoryClient;
    @Mock private NotificationClient notificationClient;
    @Mock private TransactionalOperator transactionalOperator;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                orderRepo, orderEventRepo, paymentAttemptRepo,
                airtelClient, inventoryClient, notificationClient,
                transactionalOperator
        );
        ReflectionTestUtils.setField(paymentService, "country", "ZM");
        ReflectionTestUtils.setField(paymentService, "currency", "ZMW");

        // Transactional pass-through for all tests — delegates subscription to inner Mono
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Order pendingAirtelOrder(String uuid, String customerId) {
        return Order.builder()
                .id(1L)
                .orderUuid(uuid)
                .customerId(customerId)
                .status(OrderStatus.PENDING_PAYMENT.name())
                .paymentMethod("AIRTEL_MONEY")
                .paymentStatus(PaymentStatus.PENDING.name())
                .totalAmount(new BigDecimal("100.00"))
                .deliveryFee(new BigDecimal("15.00"))
                .currency("ZMW")
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .updatedAt(LocalDateTime.now().minusSeconds(30))
                .build();
    }

    private Order confirmedOrder(String uuid) {
        return Order.builder()
                .id(2L)
                .orderUuid(uuid)
                .customerId("CUST_01")
                .status(OrderStatus.CONFIRMED.name())
                .paymentMethod("AIRTEL_MONEY")
                .paymentStatus(PaymentStatus.PAID.name())
                .totalAmount(new BigDecimal("100.00"))
                .deliveryFee(new BigDecimal("15.00"))
                .currency("ZMW")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PaymentAttempt savedAttempt(String orderUuid, String airtelRef) {
        return PaymentAttempt.builder()
                .id(10L)
                .orderUuid(orderUuid)
                .paymentPhone("0971234567")
                .airtelRef(airtelRef)
                .status(PaymentAttemptStatus.INITIATED)
                .initiatedAt(LocalDateTime.now().minusSeconds(5))
                .build();
    }

    private InitiatePaymentRequest payRequest(String phone) {
        InitiatePaymentRequest req = new InitiatePaymentRequest();
        req.setPaymentPhone(phone);
        return req;
    }

    private AirtelWebhookPayload webhook(String txId, String statusCode) {
        AirtelWebhookPayload.TransactionPayload tx = new AirtelWebhookPayload.TransactionPayload();
        tx.setId(txId);
        tx.setStatusCode(statusCode);
        AirtelWebhookPayload payload = new AirtelWebhookPayload();
        payload.setTransaction(tx);
        return payload;
    }

    // ─── initiatePayment ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiatePayment")
    class InitiatePayment {

        @Test
        @DisplayName("happy path — sends STK push, returns PUSH_SENT with masked phone")
        void happyPath_shouldSendPushAndReturnPushSent() {
            Order order = pendingAirtelOrder("ORD-001", "CUST_01");
            when(orderRepo.findByOrderUuid("ORD-001")).thenReturn(Mono.just(order));
            when(paymentAttemptRepo.findByOrderUuid("ORD-001")).thenReturn(Mono.empty());
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(airtelClient.initiateUssdPush(any())).thenReturn(Mono.just(
                    AirtelPushResponse.builder()
                            .airtelTransactionId("MOCK-ABC123")
                            .status("DP_INITIATED")
                            .build()));
            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            StepVerifier.create(paymentService.initiatePayment("ORD-001", "CUST_01", payRequest("0971234567")))
                    .assertNext(resp -> {
                        assertThat(resp.getPushStatus()).isEqualTo("PUSH_SENT");
                        assertThat(resp.getPaymentPhone()).isEqualTo("097****567");
                        assertThat(resp.getOrderUuid()).isEqualTo("ORD-001");
                    })
                    .verifyComplete();

            verify(airtelClient).initiateUssdPush(any());
            // Order must be saved with the Airtel transaction ID
            verify(orderRepo).save(argThat(o -> "MOCK-ABC123".equals(o.getAirtelTransactionId())));
        }

        @Test
        @DisplayName("order not found — throws OrderNotFoundException")
        void orderNotFound_shouldThrow() {
            when(orderRepo.findByOrderUuid("MISSING")).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.initiatePayment("MISSING", "CUST_01", payRequest("0971234567")))
                    .expectError(OrderNotFoundException.class)
                    .verify();
        }

        @Test
        @DisplayName("wrong customer ID — throws InvalidOrderStateException, no Airtel call made")
        void wrongCustomer_shouldThrowBeforeAnyExternalCall() {
            Order order = pendingAirtelOrder("ORD-002", "CUST_REAL");
            when(orderRepo.findByOrderUuid("ORD-002")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.initiatePayment("ORD-002", "CUST_WRONG", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("does not belong"))
                    .verify();

            verifyNoInteractions(paymentAttemptRepo, airtelClient);
        }

        @Test
        @DisplayName("COD order — throws InvalidOrderStateException")
        void codOrder_shouldThrow() {
            Order codOrder = pendingAirtelOrder("ORD-003", "CUST_01");
            codOrder.setPaymentMethod("COD"); // still PENDING_PAYMENT status to reach the COD check
            when(orderRepo.findByOrderUuid("ORD-003")).thenReturn(Mono.just(codOrder));

            StepVerifier.create(paymentService.initiatePayment("ORD-003", "CUST_01", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("cannot initiate"))
                    .verify();
        }

        @Test
        @DisplayName("order not PENDING_PAYMENT (e.g. CONFIRMED) — throws InvalidOrderStateException")
        void orderNotPendingPayment_shouldThrow() {
            Order confirmed = confirmedOrder("ORD-004");
            when(orderRepo.findByOrderUuid("ORD-004")).thenReturn(Mono.just(confirmed));

            StepVerifier.create(paymentService.initiatePayment("ORD-004", "CUST_01", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("CONFIRMED"))
                    .verify();
        }

        @Test
        @DisplayName("existing attempt found — blocks duplicate, throws InvalidOrderStateException")
        void existingAttempt_shouldBlockDuplicate() {
            Order order = pendingAirtelOrder("ORD-005", "CUST_01");
            when(orderRepo.findByOrderUuid("ORD-005")).thenReturn(Mono.just(order));
            when(paymentAttemptRepo.findByOrderUuid("ORD-005"))
                    .thenReturn(Mono.just(savedAttempt("ORD-005", "MOCK-EXISTING")));

            StepVerifier.create(paymentService.initiatePayment("ORD-005", "CUST_01", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("already initiated"))
                    .verify();

            verifyNoInteractions(airtelClient);
        }

        @Test
        @DisplayName("DB UNIQUE constraint fires on race — handled as duplicate, no 500")
        void uniqueConstraintViolation_shouldReturnUserFriendlyError() {
            Order order = pendingAirtelOrder("ORD-006", "CUST_01");
            when(orderRepo.findByOrderUuid("ORD-006")).thenReturn(Mono.just(order));
            when(paymentAttemptRepo.findByOrderUuid("ORD-006")).thenReturn(Mono.empty());
            when(paymentAttemptRepo.save(any()))
                    .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry")));

            StepVerifier.create(paymentService.initiatePayment("ORD-006", "CUST_01", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("already initiated"))
                    .verify();
        }

        @Test
        @DisplayName("Airtel returns null txId — attempt marked FAILED, RuntimeException thrown")
        void airtelNullTxId_shouldMarkAttemptFailedAndThrow() {
            Order order = pendingAirtelOrder("ORD-007", "CUST_01");
            when(orderRepo.findByOrderUuid("ORD-007")).thenReturn(Mono.just(order));
            when(paymentAttemptRepo.findByOrderUuid("ORD-007")).thenReturn(Mono.empty());
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(airtelClient.initiateUssdPush(any())).thenReturn(Mono.just(
                    AirtelPushResponse.builder()
                            .airtelTransactionId(null)
                            .status("UNKNOWN")
                            .build()));

            // The null-txId RuntimeException is caught by the generic onErrorResume which re-wraps it
            StepVerifier.create(paymentService.initiatePayment("ORD-007", "CUST_01", payRequest("0971234567")))
                    .expectErrorMatches(e -> e instanceof RuntimeException
                            && e.getMessage().contains("Failed to initiate Airtel payment"))
                    .verify();

            // Attempt must be saved with FAILED status
            ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
            verify(paymentAttemptRepo, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(a -> a.getStatus() == PaymentAttemptStatus.FAILED);
        }

        @Test
        @DisplayName("Airtel API unreachable — attempt marked FAILED, RuntimeException thrown")
        void airtelClientError_shouldMarkAttemptFailedAndThrow() {
            Order order = pendingAirtelOrder("ORD-008", "CUST_01");
            when(orderRepo.findByOrderUuid("ORD-008")).thenReturn(Mono.just(order));
            when(paymentAttemptRepo.findByOrderUuid("ORD-008")).thenReturn(Mono.empty());
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(airtelClient.initiateUssdPush(any()))
                    .thenReturn(Mono.error(new RuntimeException("Connection refused")));

            StepVerifier.create(paymentService.initiatePayment("ORD-008", "CUST_01", payRequest("0971234567")))
                    .expectError(RuntimeException.class)
                    .verify();

            ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
            verify(paymentAttemptRepo, atLeastOnce()).save(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(a -> a.getStatus() == PaymentAttemptStatus.FAILED);
        }
    }

    // ─── handleWebhook ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleWebhook")
    class HandleWebhook {

        @Test
        @DisplayName("missing transactionId in payload — silently ignored")
        void missingTransactionId_shouldDoNothing() {
            AirtelWebhookPayload payload = new AirtelWebhookPayload(); // transaction == null

            StepVerifier.create(paymentService.handleWebhook(payload, "{}"))
                    .verifyComplete();

            verifyNoInteractions(paymentAttemptRepo, orderRepo);
        }

        @Test
        @DisplayName("TS status — order → CONFIRMED, inventory confirmed, notification sent")
        void tsStatus_shouldConfirmOrder() {
            Order order = pendingAirtelOrder("ORD-TS", "CUST_01");
            PaymentAttempt attempt = savedAttempt("ORD-TS", "TX-SUCCESS");

            when(paymentAttemptRepo.findByAirtelRef("TX-SUCCESS")).thenReturn(Mono.just(attempt));
            when(orderRepo.findByOrderUuid("ORD-TS")).thenReturn(Mono.just(order));
            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
            when(inventoryClient.confirmReservation(anyString())).thenReturn(Mono.empty());
            when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.handleWebhook(webhook("TX-SUCCESS", "TS"), "{\"status\":\"TS\"}"))
                    .verifyComplete();

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo("PAID");
            verify(inventoryClient).confirmReservation("ORD-TS");
            verify(notificationClient).sendOrderConfirmedEvent(any());
        }

        @Test
        @DisplayName("TF status — order → CANCELLED, inventory released, no notification")
        void tfStatus_shouldCancelOrder() {
            Order order = pendingAirtelOrder("ORD-TF", "CUST_01");
            PaymentAttempt attempt = savedAttempt("ORD-TF", "TX-FAILED");

            when(paymentAttemptRepo.findByAirtelRef("TX-FAILED")).thenReturn(Mono.just(attempt));
            when(orderRepo.findByOrderUuid("ORD-TF")).thenReturn(Mono.just(order));
            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
            when(inventoryClient.cancelOrderReservations(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.handleWebhook(webhook("TX-FAILED", "TF"), "{}"))
                    .verifyComplete();

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
            assertThat(captor.getValue().getCancelledReason()).contains("TF");
            verify(inventoryClient).cancelOrderReservations("ORD-TF");
            verifyNoInteractions(notificationClient);
        }

        @Test
        @DisplayName("duplicate webhook — order already CONFIRMED, idempotency guard prevents re-processing")
        void duplicateWebhook_shouldBeNoOp() {
            Order alreadyConfirmed = confirmedOrder("ORD-DUP");
            PaymentAttempt attempt = savedAttempt("ORD-DUP", "TX-DUP");

            when(paymentAttemptRepo.findByAirtelRef("TX-DUP")).thenReturn(Mono.just(attempt));
            when(orderRepo.findByOrderUuid("ORD-DUP")).thenReturn(Mono.just(alreadyConfirmed));

            StepVerifier.create(paymentService.handleWebhook(webhook("TX-DUP", "TS"), "{}"))
                    .verifyComplete();

            verify(orderRepo, never()).save(any());
            verifyNoInteractions(inventoryClient, notificationClient);
        }

        @Test
        @DisplayName("optimistic lock conflict — concurrent handler already processed, silently no-ops")
        void optimisticLockConflict_shouldBeNoOp() {
            Order order = pendingAirtelOrder("ORD-LOCK", "CUST_01");
            PaymentAttempt attempt = savedAttempt("ORD-LOCK", "TX-LOCK");

            when(paymentAttemptRepo.findByAirtelRef("TX-LOCK")).thenReturn(Mono.just(attempt));
            when(orderRepo.findByOrderUuid("ORD-LOCK")).thenReturn(Mono.just(order));
            when(orderRepo.save(any()))
                    .thenReturn(Mono.error(new OptimisticLockingFailureException("Version mismatch")));

            // Must complete without propagating the exception
            StepVerifier.create(paymentService.handleWebhook(webhook("TX-LOCK", "TS"), "{}"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("attempt not found — falls back to order lookup by airtelTransactionId")
        void attemptNotFound_shouldFallBackToOrderLookup() {
            Order order = pendingAirtelOrder("ORD-FB", "CUST_01");
            order.setAirtelTransactionId("TX-FB");

            when(paymentAttemptRepo.findByAirtelRef("TX-FB")).thenReturn(Mono.empty());
            when(orderRepo.findByAirtelTransactionId("TX-FB")).thenReturn(Mono.just(order));
            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
            when(inventoryClient.confirmReservation(anyString())).thenReturn(Mono.empty());
            when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.handleWebhook(webhook("TX-FB", "TS"), "{}"))
                    .verifyComplete();

            // Order should still be confirmed via the fallback path
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        }
    }

    // ─── processPaymentSuccess ────────────────────────────────────────────────

    @Nested
    @DisplayName("processPaymentSuccess")
    class ProcessPaymentSuccess {

        @Test
        @DisplayName("inventory confirmation failure — logs RECONCILE NEEDED, Mono still completes")
        void inventoryFailure_shouldLogAndContinue() {
            Order order = pendingAirtelOrder("ORD-INV", "CUST_01");
            PaymentAttempt attempt = savedAttempt("ORD-INV", "TX-INV");

            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
            when(inventoryClient.confirmReservation(anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Inventory service down")));
            when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.processPaymentSuccess(order, attempt))
                    .verifyComplete();

            // Order was still saved as CONFIRMED — failure was logged, not propagated
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepo).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("notification failure — Mono still completes, order stays CONFIRMED")
        void notificationFailure_shouldContinue() {
            Order order = pendingAirtelOrder("ORD-NOTIF", "CUST_01");
            PaymentAttempt attempt = savedAttempt("ORD-NOTIF", "TX-NOTIF");

            when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
            when(inventoryClient.confirmReservation(anyString())).thenReturn(Mono.empty());
            when(notificationClient.sendOrderConfirmedEvent(any()))
                    .thenReturn(Mono.error(new RuntimeException("Kafka down")));

            StepVerifier.create(paymentService.processPaymentSuccess(order, attempt))
                    .verifyComplete();
        }
    }

    // ─── getPaymentStatus ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentStatus")
    class GetPaymentStatus {

        @Test
        @DisplayName("before /pay — returns AWAITING_PUSH (no Airtel txId yet)")
        void beforePay_shouldReturnAwaitingPush() {
            Order order = pendingAirtelOrder("ORD-PS1", "CUST_01");
            // airtelTransactionId == null — push not yet sent
            when(orderRepo.findByOrderUuid("ORD-PS1")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-PS1", "CUST_01"))
                    .assertNext(r -> assertThat(r.getPushStatus()).isEqualTo("AWAITING_PUSH"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("after /pay — returns PUSH_SENT with masked phone")
        void afterPay_shouldReturnPushSent() {
            Order order = pendingAirtelOrder("ORD-PS2", "CUST_01");
            order.setAirtelTransactionId("MOCK-XYZ");
            order.setPaymentPhone("0971234567");
            when(orderRepo.findByOrderUuid("ORD-PS2")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-PS2", "CUST_01"))
                    .assertNext(r -> {
                        assertThat(r.getPushStatus()).isEqualTo("PUSH_SENT");
                        assertThat(r.getPaymentPhone()).isEqualTo("097****567");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("after confirmed — returns CONFIRMED")
        void afterConfirm_shouldReturnConfirmed() {
            Order order = confirmedOrder("ORD-PS3");
            order.setPaymentPhone("0971234567");
            when(orderRepo.findByOrderUuid("ORD-PS3")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-PS3", "CUST_01"))
                    .assertNext(r -> assertThat(r.getPushStatus()).isEqualTo("CONFIRMED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("cancelled order — returns FAILED push status")
        void cancelledOrder_shouldReturnFailed() {
            Order order = pendingAirtelOrder("ORD-PS4", "CUST_01");
            order.setStatus(OrderStatus.CANCELLED.name());
            when(orderRepo.findByOrderUuid("ORD-PS4")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-PS4", "CUST_01"))
                    .assertNext(r -> assertThat(r.getPushStatus()).isEqualTo("FAILED"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("COD order — throws InvalidOrderStateException")
        void codOrder_shouldThrow() {
            Order codOrder = Order.builder()
                    .id(5L).orderUuid("ORD-COD").customerId("CUST_01")
                    .status(OrderStatus.CONFIRMED.name()).paymentMethod("COD")
                    .paymentStatus(PaymentStatus.COD_PENDING.name())
                    .totalAmount(BigDecimal.TEN).deliveryFee(BigDecimal.ZERO).currency("ZMW")
                    .build();
            when(orderRepo.findByOrderUuid("ORD-COD")).thenReturn(Mono.just(codOrder));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-COD", "CUST_01"))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("COD"))
                    .verify();
        }

        @Test
        @DisplayName("wrong customer — throws InvalidOrderStateException")
        void wrongCustomer_shouldThrow() {
            Order order = pendingAirtelOrder("ORD-WC", "CUST_REAL");
            when(orderRepo.findByOrderUuid("ORD-WC")).thenReturn(Mono.just(order));

            StepVerifier.create(paymentService.getPaymentStatus("ORD-WC", "CUST_WRONG"))
                    .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                            && e.getMessage().contains("does not belong"))
                    .verify();
        }

        @Test
        @DisplayName("order not found — throws OrderNotFoundException")
        void orderNotFound_shouldThrow() {
            when(orderRepo.findByOrderUuid("GONE")).thenReturn(Mono.empty());

            StepVerifier.create(paymentService.getPaymentStatus("GONE", "CUST_01"))
                    .expectError(OrderNotFoundException.class)
                    .verify();
        }
    }
}
