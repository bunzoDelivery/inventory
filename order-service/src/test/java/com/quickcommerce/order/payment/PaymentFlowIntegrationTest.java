package com.quickcommerce.order.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.quickcommerce.order.BaseIntegrationTest;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the Airtel Money payment flow.
 *
 * Uses:
 * - Real MySQL via Testcontainers (shared with other integration tests)
 * - MockAirtelMoneyClient (active on default mock-airtel profile)
 * - MockWebServer for product-service and inventory-service
 * - NoOpAirtelWebhookValidator (always passes)
 * - NoOpNotificationClient (no HTTP calls)
 */
@AutoConfigureWebTestClient
class PaymentFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void setUp() throws IOException {
        startMockServers();
    }

    @AfterAll
    static void tearDown() throws IOException {
        stopMockServers();
    }

    @DynamicPropertySource
    static void configureClientProperties(DynamicPropertyRegistry registry) {
        registry.add("client.product-service.url", () -> mockProductService.url("/").toString());
        registry.add("client.inventory-service.url", () -> mockInventoryService.url("/").toString());
    }

    @BeforeEach
    void clearDb() {
        cleanDatabase();
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full Airtel TS flow: create order → pay → webhook TS → order CONFIRMED")
    void airtelPayment_webhookTS_shouldConfirmOrder() throws JsonProcessingException {
        // Given: create a PENDING_PAYMENT Airtel order
        String orderUuid = createAirtelOrder("CUST_TS");

        // When: initiate payment
        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_TS")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("PUSH_SENT");
                    assertThat(resp.getPaymentPhone()).isEqualTo("097****567");
                });

        // Read the Airtel transaction ID saved by MockAirtelMoneyClient
        Order order = orderRepository.findByOrderUuid(orderUuid).block();
        assertThat(order).isNotNull();
        String txId = order.getAirtelTransactionId();
        assertThat(txId).startsWith("MOCK-");

        // Enqueue inventory confirmReservation (called by processPaymentSuccess)
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        // When: Airtel POSTs a TS webhook
        String webhookBody = buildWebhookJson(txId, "TS");
        webTestClient.post()
                .uri("/api/v1/webhooks/airtel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(webhookBody)
                .exchange()
                // Airtel webhook always returns 200 regardless of outcome
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(resp -> assertThat(resp.get("status")).isEqualTo("ACCEPTED"));

        // Then: poll payment status → CONFIRMED
        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_TS")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("CONFIRMED");
                    assertThat(resp.getOrderStatus()).isEqualTo("CONFIRMED");
                    assertThat(resp.getPaymentStatus()).isEqualTo("PAID");
                });

        // And: verify payment attempt in DB
        PaymentAttempt attempt = paymentAttemptRepository.findByOrderUuid(orderUuid).block();
        assertThat(attempt).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(attempt.getAirtelRef()).isEqualTo(txId);
        assertThat(attempt.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("Full Airtel TF flow: create order → pay → webhook TF → order CANCELLED")
    void airtelPayment_webhookTF_shouldCancelOrder() throws JsonProcessingException {
        // Given
        String orderUuid = createAirtelOrder("CUST_TF");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_TF")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isOk();

        String txId = orderRepository.findByOrderUuid(orderUuid).block().getAirtelTransactionId();

        // Enqueue inventory cancelReservations (called by processPaymentFailure)
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        // When: Airtel sends a TF (failure) webhook
        webTestClient.post()
                .uri("/api/v1/webhooks/airtel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildWebhookJson(txId, "TF"))
                .exchange()
                .expectStatus().isOk();

        // Then: order is CANCELLED and payment status is FAILED
        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_TF")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("FAILED");
                    assertThat(resp.getOrderStatus()).isEqualTo("CANCELLED");
                    assertThat(resp.getPaymentStatus()).isEqualTo("FAILED");
                });

        // And: attempt is marked FAILED
        PaymentAttempt attempt = paymentAttemptRepository.findByOrderUuid(orderUuid).block();
        assertThat(attempt).isNotNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.FAILED);
    }

    // ─── Payment status polling ───────────────────────────────────────────────

    @Test
    @DisplayName("Poll payment-status before /pay — returns AWAITING_PUSH")
    void paymentStatus_beforePush_shouldReturnAwaitingPush() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_POLL1");

        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_POLL1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("AWAITING_PUSH");
                    assertThat(resp.getOrderStatus()).isEqualTo("PENDING_PAYMENT");
                });
    }

    @Test
    @DisplayName("Poll payment-status after /pay — returns PUSH_SENT")
    void paymentStatus_afterPush_shouldReturnPushSent() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_POLL2");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_POLL2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_POLL2")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("PUSH_SENT");
                    assertThat(resp.getPaymentPhone()).isEqualTo("097****567");
                });
    }

    // ─── Authentication / header guards ──────────────────────────────────────

    @Test
    @DisplayName("POST /pay without X-Customer-Id header — returns HTTP 400")
    void initiatePayment_missingCustomerIdHeader_shouldReturn400() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_NOHEADER");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                // No X-Customer-Id header
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /pay with wrong X-Customer-Id — returns HTTP 400")
    void initiatePayment_wrongCustomerId_shouldReturn400() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_OWNER");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_THIEF")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat((String) body.get("error")).contains("does not belong"));
    }

    @Test
    @DisplayName("GET /payment-status with wrong X-Customer-Id — returns HTTP 400")
    void paymentStatus_wrongCustomerId_shouldReturn400() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_OWNER2");

        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_THIEF")
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ─── Idempotency / duplicate guard ───────────────────────────────────────

    @Test
    @DisplayName("POST /pay twice for same order — second call returns HTTP 400")
    void initiatePayment_duplicatePush_shouldReturn400() throws JsonProcessingException {
        String orderUuid = createAirtelOrder("CUST_DEDUP");

        // First push — should succeed
        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_DEDUP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isOk();

        // Second push — should be blocked
        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_DEDUP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(body -> assertThat((String) body.get("error")).contains("already initiated"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Creates an AIRTEL_MONEY order (PENDING_PAYMENT status) for the given customer.
     * Enqueues the required product-service and inventory-service mock responses.
     *
     * @return the orderUuid of the created order
     */
    private String createAirtelOrder(String customerId) throws JsonProcessingException {
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse response = webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest(customerId, "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        return response.getOrderId();
    }

    private String buildWebhookJson(String txId, String statusCode) {
        return String.format(
                "{\"transaction\":{\"id\":\"%s\",\"status_code\":\"%s\",\"message\":\"Test\"}}",
                txId, statusCode);
    }
}
