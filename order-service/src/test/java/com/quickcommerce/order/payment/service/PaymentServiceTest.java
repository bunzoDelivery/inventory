package com.quickcommerce.order.payment.service;

import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.client.NotificationClient;
import com.quickcommerce.order.domain.*;
import com.quickcommerce.order.exception.InvalidOrderStateException;
import com.quickcommerce.order.exception.OrderNotFoundException;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.config.PaymentMethodsConfig;
import com.quickcommerce.order.payment.dto.AvailablePaymentMethodsResponse;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.gateway.*;
import com.quickcommerce.order.payment.repository.PaymentAttemptRepository;
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

        @Mock
        private OrderRepository orderRepo;
        @Mock
        private OrderEventRepository orderEventRepo;
        @Mock
        private PaymentAttemptRepository paymentAttemptRepo;
        @Mock
        private PaymentGatewayRouter gatewayRouter;
        @Mock
        private PaymentGateway mockGateway;
        @Mock
        private InventoryClient inventoryClient;
        @Mock
        private NotificationClient notificationClient;
        @Mock
        private TransactionalOperator transactionalOperator;
        @Mock
        private PaymentMethodsConfig paymentMethodsConfig;

        private PaymentService paymentService;

        @BeforeEach
        void setUp() {
                paymentService = new PaymentService(
                                orderRepo, orderEventRepo, paymentAttemptRepo,
                                gatewayRouter, inventoryClient, notificationClient,
                                transactionalOperator, paymentMethodsConfig);

                // By default, the router returns our mockGateway (PAWAPAY) for new payments
                lenient().when(gatewayRouter.resolve()).thenReturn(mockGateway);
                lenient().when(mockGateway.getGatewayName()).thenReturn(GatewayName.PAWAPAY);

                // Transactional pass-through for all tests
                lenient().when(transactionalOperator.transactional(any(Mono.class)))
                                .thenAnswer(inv -> inv.getArgument(0));
        }

        // ─── Helpers ──────────────────────────────────────────────────────────────

        private Order pendingMobileMoneyOrder(String uuid, String customerId) {
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

        private PaymentAttempt savedAttempt(String orderUuid, String gatewayRef) {
                return PaymentAttempt.builder()
                                .id(10L)
                                .orderUuid(orderUuid)
                                .paymentPhone("0971234567")
                                .gatewayRef(gatewayRef)
                                .gatewayUsed(GatewayName.PAWAPAY)
                                .mobileNetwork(MobileNetwork.AIRTEL)
                                .status(PaymentAttemptStatus.INITIATED)
                                .initiatedAt(LocalDateTime.now().minusSeconds(5))
                                .build();
        }

        private InitiatePaymentRequest payRequest(String phone) {
                InitiatePaymentRequest req = new InitiatePaymentRequest();
                req.setPaymentPhone(phone);
                req.setMobileNetwork(MobileNetwork.AIRTEL);
                return req;
        }

        // ─── initiatePayment ──────────────────────────────────────────────────────

        @Nested
        @DisplayName("initiatePayment")
        class InitiatePayment {

                @Test
                @DisplayName("happy path — sends push, returns PUSH_SENT with masked phone and network")
                void happyPath_shouldSendPushAndReturnPushSent() {
                        Order order = pendingMobileMoneyOrder("ORD-001", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-001")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-001")).thenReturn(Mono.empty());
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(mockGateway.initiatePayment(any())).thenReturn(Mono.just(
                                        GatewayPaymentResponse.builder().gatewayRef("PAWA-REF-001").build()));
                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-001", "CUST_01", payRequest("0971234567")))
                                        .assertNext(resp -> {
                                                assertThat(resp.getPushStatus()).isEqualTo("PUSH_SENT");
                                                assertThat(resp.getPaymentPhone()).isEqualTo("097****567");
                                                assertThat(resp.getOrderUuid()).isEqualTo("ORD-001");
                                                assertThat(resp.getMobileNetwork()).isEqualTo("AIRTEL");
                                        })
                                        .verifyComplete();

                        verify(mockGateway).initiatePayment(any());
                        // Order must be saved with the gateway transaction ID
                        verify(orderRepo).save(argThat(o -> "PAWA-REF-001".equals(o.getGatewayTransactionId())));
                }

                @Test
                @DisplayName("gateway request carries correct network and orderUuid")
                void happyPath_shouldPassCorrectGatewayRequest() {
                        Order order = pendingMobileMoneyOrder("ORD-NET", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-NET")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-NET")).thenReturn(Mono.empty());
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(mockGateway.initiatePayment(any())).thenReturn(Mono.just(
                                        GatewayPaymentResponse.builder().gatewayRef("PAWA-NET-001").build()));
                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

                        InitiatePaymentRequest mtnReq = new InitiatePaymentRequest();
                        mtnReq.setPaymentPhone("0761234567");
                        mtnReq.setMobileNetwork(MobileNetwork.MTN);

                        StepVerifier.create(paymentService.initiatePayment("ORD-NET", "CUST_01", mtnReq))
                                        .assertNext(resp -> assertThat(resp.getMobileNetwork()).isEqualTo("MTN"))
                                        .verifyComplete();

                        ArgumentCaptor<GatewayPaymentRequest> captor = ArgumentCaptor
                                        .forClass(GatewayPaymentRequest.class);
                        verify(mockGateway).initiatePayment(captor.capture());
                        assertThat(captor.getValue().getMobileNetwork()).isEqualTo(MobileNetwork.MTN);
                        assertThat(captor.getValue().getOrderUuid()).isEqualTo("ORD-NET");
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
                @DisplayName("wrong customer — throws InvalidOrderStateException, no gateway push made")
                void wrongCustomer_shouldThrowBeforeAnyExternalCall() {
                        Order order = pendingMobileMoneyOrder("ORD-002", "CUST_REAL");
                        when(orderRepo.findByOrderUuid("ORD-002")).thenReturn(Mono.just(order));

                        StepVerifier.create(paymentService.initiatePayment("ORD-002", "CUST_WRONG",
                                        payRequest("0971234567")))
                                        .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                                                        && e.getMessage().contains("does not belong"))
                                        .verify();

                        // Auth guard fires before reaching the gateway — no push should be initiated
                        verifyNoInteractions(paymentAttemptRepo);
                        verify(mockGateway, never()).initiatePayment(any());
                }

                @Test
                @DisplayName("COD order — throws InvalidOrderStateException")
                void codOrder_shouldThrow() {
                        Order codOrder = pendingMobileMoneyOrder("ORD-003", "CUST_01");
                        codOrder.setPaymentMethod("COD");
                        when(orderRepo.findByOrderUuid("ORD-003")).thenReturn(Mono.just(codOrder));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-003", "CUST_01", payRequest("0971234567")))
                                        .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                                                        && e.getMessage().contains("cannot initiate"))
                                        .verify();
                }

                @Test
                @DisplayName("order not PENDING_PAYMENT — throws InvalidOrderStateException")
                void orderNotPendingPayment_shouldThrow() {
                        Order confirmed = confirmedOrder("ORD-004");
                        when(orderRepo.findByOrderUuid("ORD-004")).thenReturn(Mono.just(confirmed));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-004", "CUST_01", payRequest("0971234567")))
                                        .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                                                        && e.getMessage().contains("CONFIRMED"))
                                        .verify();
                }

                @Test
                @DisplayName("existing attempt found — blocks duplicate, throws InvalidOrderStateException")
                void existingAttempt_shouldBlockDuplicate() {
                        Order order = pendingMobileMoneyOrder("ORD-005", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-005")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-005"))
                                        .thenReturn(Mono.just(savedAttempt("ORD-005", "PAWA-EXISTING")));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-005", "CUST_01", payRequest("0971234567")))
                                        .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                                                        && e.getMessage().contains("already initiated"))
                                        .verify();

                        // Duplicate guard fires before reaching the gateway — no push should be
                        // initiated
                        verify(mockGateway, never()).initiatePayment(any());
                }

                @Test
                @DisplayName("DB UNIQUE constraint fires on race — handled as duplicate, no 500")
                void uniqueConstraintViolation_shouldReturnUserFriendlyError() {
                        Order order = pendingMobileMoneyOrder("ORD-006", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-006")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-006")).thenReturn(Mono.empty());
                        when(paymentAttemptRepo.save(any()))
                                        .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate entry")));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-006", "CUST_01", payRequest("0971234567")))
                                        .expectErrorMatches(e -> e instanceof InvalidOrderStateException
                                                        && e.getMessage().contains("already initiated"))
                                        .verify();
                }

                @Test
                @DisplayName("gateway returns error — attempt marked FAILED, PaymentGatewayException thrown")
                void gatewayError_shouldMarkAttemptFailedAndThrow() {
                        Order order = pendingMobileMoneyOrder("ORD-007", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-007")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-007")).thenReturn(Mono.empty());
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(mockGateway.initiatePayment(any()))
                                        .thenReturn(Mono.error(new RuntimeException("Connection refused")));

                        StepVerifier.create(
                                        paymentService.initiatePayment("ORD-007", "CUST_01", payRequest("0971234567")))
                                        .expectError(RuntimeException.class)
                                        .verify();

                        ArgumentCaptor<PaymentAttempt> captor = ArgumentCaptor.forClass(PaymentAttempt.class);
                        verify(paymentAttemptRepo, atLeastOnce()).save(captor.capture());
                        assertThat(captor.getAllValues()).anyMatch(a -> a.getStatus() == PaymentAttemptStatus.FAILED);
                }
        }

        // ─── processPaymentResult ─────────────────────────────────────────────────

        @Nested
        @DisplayName("processPaymentResult")
        class ProcessPaymentResult {

                @Test
                @DisplayName("missing gatewayRef — silently ignored")
                void missingGatewayRef_shouldDoNothing() {
                        StepVerifier.create(paymentService.processPaymentResult(null, true, "COMPLETED", "{}", "TEST"))
                                        .verifyComplete();

                        verifyNoInteractions(paymentAttemptRepo, orderRepo);
                }

                @Test
                @DisplayName("SUCCESS result — order → CONFIRMED, inventory confirmed, notification sent")
                void successResult_shouldConfirmOrder() {
                        Order order = pendingMobileMoneyOrder("ORD-TS", "CUST_01");
                        PaymentAttempt attempt = savedAttempt("ORD-TS", "PAWA-SUCCESS");

                        when(paymentAttemptRepo.findByGatewayRef("PAWA-SUCCESS")).thenReturn(Mono.just(attempt));
                        when(orderRepo.findByOrderUuid("ORD-TS")).thenReturn(Mono.just(order));
                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
                        when(inventoryClient.confirmReservation(anyString())).thenReturn(Mono.empty());
                        when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

                        StepVerifier.create(paymentService.processPaymentResult(
                                        "PAWA-SUCCESS", true, "COMPLETED", "{}", "PAWAPAY_WEBHOOK"))
                                        .verifyComplete();

                        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
                        verify(orderRepo).save(captor.capture());
                        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
                        assertThat(captor.getValue().getPaymentStatus()).isEqualTo("PAID");
                        verify(inventoryClient).confirmReservation("ORD-TS");
                        verify(notificationClient).sendOrderConfirmedEvent(any());
                }

                @Test
                @DisplayName("FAILED result — order → CANCELLED, inventory released, actor from gateway_used")
                void failedResult_shouldCancelOrderWithCorrectActor() {
                        Order order = pendingMobileMoneyOrder("ORD-TF", "CUST_01");
                        PaymentAttempt attempt = savedAttempt("ORD-TF", "PAWA-FAILED");

                        when(paymentAttemptRepo.findByGatewayRef("PAWA-FAILED")).thenReturn(Mono.just(attempt));
                        when(orderRepo.findByOrderUuid("ORD-TF")).thenReturn(Mono.just(order));
                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
                        when(inventoryClient.cancelOrderReservations(anyString())).thenReturn(Mono.empty());

                        StepVerifier.create(paymentService.processPaymentResult(
                                        "PAWA-FAILED", false, "FAILED", "{}", "PAWAPAY_WEBHOOK"))
                                        .verifyComplete();

                        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
                        verify(orderRepo).save(captor.capture());
                        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
                        assertThat(captor.getValue().getCancelledReason()).contains("FAILED");
                        verify(inventoryClient).cancelOrderReservations("ORD-TF");
                        verifyNoInteractions(notificationClient);
                }

                @Test
                @DisplayName("duplicate webhook — order already CONFIRMED, idempotency guard no-ops")
                void duplicateWebhook_shouldBeNoOp() {
                        Order alreadyConfirmed = confirmedOrder("ORD-DUP");
                        PaymentAttempt attempt = savedAttempt("ORD-DUP", "PAWA-DUP");

                        when(paymentAttemptRepo.findByGatewayRef("PAWA-DUP")).thenReturn(Mono.just(attempt));
                        when(orderRepo.findByOrderUuid("ORD-DUP")).thenReturn(Mono.just(alreadyConfirmed));

                        StepVerifier.create(paymentService.processPaymentResult(
                                        "PAWA-DUP", true, "COMPLETED", "{}", "PAWAPAY_WEBHOOK"))
                                        .verifyComplete();

                        verify(orderRepo, never()).save(any());
                        verifyNoInteractions(inventoryClient, notificationClient);
                }

                @Test
                @DisplayName("optimistic lock conflict — concurrent handler already processed, no-ops")
                void optimisticLockConflict_shouldBeNoOp() {
                        Order order = pendingMobileMoneyOrder("ORD-LOCK", "CUST_01");
                        PaymentAttempt attempt = savedAttempt("ORD-LOCK", "PAWA-LOCK");

                        when(paymentAttemptRepo.findByGatewayRef("PAWA-LOCK")).thenReturn(Mono.just(attempt));
                        when(orderRepo.findByOrderUuid("ORD-LOCK")).thenReturn(Mono.just(order));
                        when(orderRepo.save(any()))
                                        .thenReturn(Mono.error(
                                                        new OptimisticLockingFailureException("Version mismatch")));

                        StepVerifier.create(paymentService.processPaymentResult(
                                        "PAWA-LOCK", true, "COMPLETED", "{}", "PAWAPAY_WEBHOOK"))
                                        .verifyComplete();
                }

                @Test
                @DisplayName("attempt not found — falls back to order lookup by gatewayTransactionId")
                void attemptNotFound_shouldFallBackToOrderLookup() {
                        Order order = pendingMobileMoneyOrder("ORD-FB", "CUST_01");
                        order.setGatewayTransactionId("PAWA-FB");

                        when(paymentAttemptRepo.findByGatewayRef("PAWA-FB")).thenReturn(Mono.empty());
                        when(orderRepo.findByGatewayTransactionId("PAWA-FB")).thenReturn(Mono.just(order));
                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
                        when(inventoryClient.confirmReservation(anyString())).thenReturn(Mono.empty());
                        when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

                        StepVerifier.create(paymentService.processPaymentResult(
                                        "PAWA-FB", true, "COMPLETED", "{}", "PAWAPAY_WEBHOOK"))
                                        .verifyComplete();

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
                        Order order = pendingMobileMoneyOrder("ORD-INV", "CUST_01");
                        PaymentAttempt attempt = savedAttempt("ORD-INV", "PAWA-INV");

                        when(orderRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(paymentAttemptRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
                        when(orderEventRepo.save(any())).thenReturn(Mono.just(new OrderEvent()));
                        when(inventoryClient.confirmReservation(anyString()))
                                        .thenReturn(Mono.error(new RuntimeException("Inventory service down")));
                        when(notificationClient.sendOrderConfirmedEvent(any())).thenReturn(Mono.empty());

                        StepVerifier.create(paymentService.processPaymentSuccess(order, attempt))
                                        .verifyComplete();

                        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
                        verify(orderRepo).save(captor.capture());
                        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
                }

                @Test
                @DisplayName("notification failure — Mono still completes, order stays CONFIRMED")
                void notificationFailure_shouldContinue() {
                        Order order = pendingMobileMoneyOrder("ORD-NOTIF", "CUST_01");
                        PaymentAttempt attempt = savedAttempt("ORD-NOTIF", "PAWA-NOTIF");

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

                @BeforeEach
                void stubAttemptLookup() {
                        // getPaymentStatus() calls findByOrderUuid to populate mobileNetwork.
                        // Default to empty (no attempt yet) so existing tests still reach
                        // defaultIfEmpty.
                        lenient().when(paymentAttemptRepo.findByOrderUuid(anyString()))
                                        .thenReturn(Mono.empty());
                }

                @Test
                @DisplayName("before /pay — returns AWAITING_PUSH (no gatewayTransactionId yet)")
                void beforePay_shouldReturnAwaitingPush() {
                        Order order = pendingMobileMoneyOrder("ORD-PS1", "CUST_01");
                        when(orderRepo.findByOrderUuid("ORD-PS1")).thenReturn(Mono.just(order));

                        StepVerifier.create(paymentService.getPaymentStatus("ORD-PS1", "CUST_01"))
                                        .assertNext(r -> assertThat(r.getPushStatus()).isEqualTo("AWAITING_PUSH"))
                                        .verifyComplete();
                }

                @Test
                @DisplayName("after /pay — returns PUSH_SENT with masked phone")
                void afterPay_shouldReturnPushSent() {
                        Order order = pendingMobileMoneyOrder("ORD-PS2", "CUST_01");
                        order.setGatewayTransactionId("PAWA-XYZ");
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
                        Order order = pendingMobileMoneyOrder("ORD-PS4", "CUST_01");
                        order.setStatus(OrderStatus.CANCELLED.name());
                        when(orderRepo.findByOrderUuid("ORD-PS4")).thenReturn(Mono.just(order));

                        StepVerifier.create(paymentService.getPaymentStatus("ORD-PS4", "CUST_01"))
                                        .assertNext(r -> assertThat(r.getPushStatus()).isEqualTo("FAILED"))
                                        .verifyComplete();
                }

                @Test
                @DisplayName("with existing attempt — mobileNetwork is populated in response")
                void withAttempt_shouldIncludeMobileNetwork() {
                        Order order = pendingMobileMoneyOrder("ORD-MN", "CUST_01");
                        order.setGatewayTransactionId("PAWA-MN");
                        order.setPaymentPhone("0971234567");
                        PaymentAttempt attempt = savedAttempt("ORD-MN", "PAWA-MN"); // has MobileNetwork.AIRTEL

                        when(orderRepo.findByOrderUuid("ORD-MN")).thenReturn(Mono.just(order));
                        when(paymentAttemptRepo.findByOrderUuid("ORD-MN")).thenReturn(Mono.just(attempt));

                        StepVerifier.create(paymentService.getPaymentStatus("ORD-MN", "CUST_01"))
                                        .assertNext(r -> {
                                                assertThat(r.getMobileNetwork()).isEqualTo("AIRTEL");
                                                assertThat(r.getPushStatus()).isEqualTo("PUSH_SENT");
                                        })
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
                        Order order = pendingMobileMoneyOrder("ORD-WC", "CUST_REAL");
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

        // ─── getAvailablePaymentMethods
        // ─────────────────────────────────────────────────

        @Nested
        @DisplayName("getAvailablePaymentMethods")
        class GetAvailablePaymentMethods {

        @Test
        @DisplayName("all methods enabled — all three entries have enabled=true")
        void allEnabled_shouldReturnThreeEnabledEntries() {
            when(paymentMethodsConfig.isEnabled(PaymentMethod.COD)).thenReturn(true);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.AIRTEL_MONEY)).thenReturn(true);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.MTN_MONEY)).thenReturn(true);

            AvailablePaymentMethodsResponse response = paymentService.getAvailablePaymentMethods();

            assertThat(response.getMethods()).hasSize(3);
            assertThat(response.getMethods()).allMatch(AvailablePaymentMethodsResponse.PaymentMethodEntry::isEnabled);
            assertThat(response.getMethods()).extracting("code")
                    .containsExactly("COD", "AIRTEL_MONEY", "MTN_MONEY");
        }

        @Test
        @DisplayName("MTN disabled — only MTN_MONEY entry has enabled=false")
        void mtnDisabled_shouldReturnMtnAsDisabled() {
            when(paymentMethodsConfig.isEnabled(PaymentMethod.COD)).thenReturn(true);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.AIRTEL_MONEY)).thenReturn(true);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.MTN_MONEY)).thenReturn(false);

            AvailablePaymentMethodsResponse response = paymentService.getAvailablePaymentMethods();

            assertThat(response.getMethods()).hasSize(3);
            assertThat(response.getMethods()).filteredOn(m -> m.getCode().equals("MTN_MONEY"))
                    .allMatch(m -> !m.isEnabled());
            assertThat(response.getMethods()).filteredOn(m -> !m.getCode().equals("MTN_MONEY"))
                    .allMatch(AvailablePaymentMethodsResponse.PaymentMethodEntry::isEnabled);
        }

        @Test
        @DisplayName("only COD enabled — mobile money entries have enabled=false")
        void onlyCodEnabled_shouldReturnMobileMoneyDisabled() {
            when(paymentMethodsConfig.isEnabled(PaymentMethod.COD)).thenReturn(true);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.AIRTEL_MONEY)).thenReturn(false);
            when(paymentMethodsConfig.isEnabled(PaymentMethod.MTN_MONEY)).thenReturn(false);

            AvailablePaymentMethodsResponse response = paymentService.getAvailablePaymentMethods();

            assertThat(response.getMethods()).hasSize(3);
            assertThat(response.getMethods()).filteredOn(m -> m.getCode().equals("COD"))
                    .allMatch(AvailablePaymentMethodsResponse.PaymentMethodEntry::isEnabled);
            assertThat(response.getMethods()).filteredOn(m -> !m.getCode().equals("COD"))
                    .allMatch(m -> !m.isEnabled());
        }
        }
}
