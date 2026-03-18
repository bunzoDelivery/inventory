package com.quickcommerce.order.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.quickcommerce.order.BaseIntegrationTest;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.payment.domain.PaymentAttempt;
import com.quickcommerce.order.payment.domain.PaymentAttemptStatus;
import com.quickcommerce.order.payment.dto.PaymentStatusResponse;
import com.quickcommerce.order.payment.gateway.GatewayName;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the PawaPay payment flow.
 *
 * Uses MockPawaPayGateway (active on mock-pawapay profile) so no real
 * PawaPay credentials are needed. Mirrors the Airtel flow tests but exercises
 * the /webhooks/pawapay endpoint and PawaPay JSON format.
 */
@ActiveProfiles("mock-pawapay")
@AutoConfigureWebTestClient
class PawaPayWebhookFlowIntegrationTest extends BaseIntegrationTest {

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
        registry.add("payment.active-gateway", () -> "PAWAPAY");
    }

    @BeforeEach
    void clearDb() {
        cleanDatabase();
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Full PawaPay COMPLETED flow: create → pay → webhook COMPLETED → order CONFIRMED")
    void pawaPayPayment_webhookCompleted_shouldConfirmOrder() throws JsonProcessingException {
        String orderUuid = createMobileMoneyOrder("CUST_PAWA_OK", "AIRTEL_MONEY");

        // Initiate payment — MockPawaPayGateway returns orderUuid as depositId
        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_OK")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567", "mobileNetwork", "AIRTEL"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("PUSH_SENT");
                    assertThat(resp.getMobileNetwork()).isEqualTo("AIRTEL");
                });

        // Verify gatewayTransactionId = orderUuid (PawaPay pattern)
        Order order = orderRepository.findByOrderUuid(orderUuid).block();
        assertThat(order).isNotNull();
        assertThat(order.getGatewayTransactionId()).isEqualTo(orderUuid);

        // Verify attempt stored with PAWAPAY as gateway_used
        PaymentAttempt attempt = paymentAttemptRepository.findByOrderUuid(orderUuid).block();
        assertThat(attempt).isNotNull();
        assertThat(attempt.getGatewayUsed()).isEqualTo(GatewayName.PAWAPAY);
        assertThat(attempt.getGatewayRef()).isEqualTo(orderUuid);
        assertThat(attempt.getMobileNetwork().name()).isEqualTo("AIRTEL");

        // Enqueue inventory confirmation
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        // PawaPay POSTs webhook with depositId = orderUuid
        String webhookBody = buildPawaPayWebhookJson(orderUuid, "COMPLETED");
        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(webhookBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(resp -> assertThat(resp.get("status")).isEqualTo("ACCEPTED"));

        // Poll: should be CONFIRMED
        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_OK")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("CONFIRMED");
                    assertThat(resp.getOrderStatus()).isEqualTo("CONFIRMED");
                    assertThat(resp.getPaymentStatus()).isEqualTo("PAID");
                });

        // DB: attempt resolved as SUCCESS
        PaymentAttempt resolved = paymentAttemptRepository.findByOrderUuid(orderUuid).block();
        assertThat(resolved).isNotNull();
        assertThat(resolved.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("PawaPay FAILED webhook — order → CANCELLED, inventory released")
    void pawaPayPayment_webhookFailed_shouldCancelOrder() throws JsonProcessingException {
        String orderUuid = createMobileMoneyOrder("CUST_PAWA_FAIL", "AIRTEL_MONEY");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_FAIL")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567", "mobileNetwork", "AIRTEL"))
                .exchange()
                .expectStatus().isOk();

        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPawaPayWebhookJson(orderUuid, "FAILED"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_FAIL")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> {
                    assertThat(resp.getPushStatus()).isEqualTo("FAILED");
                    assertThat(resp.getOrderStatus()).isEqualTo("CANCELLED");
                });
    }

    @Test
    @DisplayName("PawaPay DUPLICATE_IGNORED webhook — treated as no-op, order unchanged")
    void pawaPayPayment_webhookDuplicateIgnored_shouldBeNoOp() throws JsonProcessingException {
        String orderUuid = createMobileMoneyOrder("CUST_PAWA_DUP", "AIRTEL_MONEY");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_DUP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567", "mobileNetwork", "AIRTEL"))
                .exchange()
                .expectStatus().isOk();

        // DUPLICATE_IGNORED — no inventory call expected, order stays PENDING_PAYMENT
        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPawaPayWebhookJson(orderUuid, "DUPLICATE_IGNORED"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(resp -> assertThat(resp.get("status")).isEqualTo("ACCEPTED"));

        // Order should still be PENDING_PAYMENT
        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_DUP")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> assertThat(resp.getOrderStatus()).isEqualTo("PENDING_PAYMENT"));
    }

    @Test
    @DisplayName("Duplicate PawaPay COMPLETED webhook — second webhook is idempotent, no double-confirm")
    void pawaPayPayment_duplicateCompletedWebhook_shouldBeIdempotent() throws JsonProcessingException {
        String orderUuid = createMobileMoneyOrder("CUST_PAWA_IDEM", "AIRTEL_MONEY");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_IDEM")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0971234567", "mobileNetwork", "AIRTEL"))
                .exchange()
                .expectStatus().isOk();

        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        // First COMPLETED webhook
        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPawaPayWebhookJson(orderUuid, "COMPLETED"))
                .exchange()
                .expectStatus().isOk();

        // Second COMPLETED webhook (PawaPay retry)
        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPawaPayWebhookJson(orderUuid, "COMPLETED"))
                .exchange()
                .expectStatus().isOk();

        // Must still be CONFIRMED exactly once
        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_PAWA_IDEM")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> assertThat(resp.getOrderStatus()).isEqualTo("CONFIRMED"));
    }

    @Test
    @DisplayName("MTN network payment via PawaPay — pay and confirm works end-to-end")
    void pawaPayMtn_payment_shouldWork() throws JsonProcessingException {
        String orderUuid = createMobileMoneyOrder("CUST_MTN", "MTN_MONEY");

        webTestClient.post()
                .uri("/api/v1/orders/{uuid}/pay", orderUuid)
                .header("X-Customer-Id", "CUST_MTN")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("paymentPhone", "0761234567", "mobileNetwork", "MTN"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> assertThat(resp.getMobileNetwork()).isEqualTo("MTN"));

        PaymentAttempt attempt = paymentAttemptRepository.findByOrderUuid(orderUuid).block();
        assertThat(attempt).isNotNull();
        assertThat(attempt.getMobileNetwork().name()).isEqualTo("MTN");

        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        webTestClient.post()
                .uri("/api/v1/webhooks/pawapay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPawaPayWebhookJson(orderUuid, "COMPLETED"))
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/api/v1/orders/{uuid}/payment-status", orderUuid)
                .header("X-Customer-Id", "CUST_MTN")
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentStatusResponse.class)
                .value(resp -> assertThat(resp.getOrderStatus()).isEqualTo("CONFIRMED"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String createMobileMoneyOrder(String customerId, String paymentMethod)
            throws JsonProcessingException {
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse response = webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest(customerId, paymentMethod))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        return response.getOrderId();
    }

    private String buildPawaPayWebhookJson(String depositId, String status) {
        return String.format(
                "{\"depositId\":\"%s\",\"status\":\"%s\",\"amount\":\"115.00\",\"currency\":\"ZMW\"," +
                "\"correspondent\":\"AIRTEL_ZAMBIA\"}",
                depositId, status);
    }
}
