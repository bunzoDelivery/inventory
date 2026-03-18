package com.quickcommerce.order.payment.gateway;

import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.payment.gateway.pawapay.PawaPayGateway;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PawaPayGateway} using MockWebServer to verify
 * the exact HTTP request shape and response mapping.
 *
 * Each test gets a fresh MockWebServer instance so that unconsumed requests
 * from one test never bleed into the next.
 */
class PawaPayGatewayTest {

    // Per-test instance — prevents cross-test request queue contamination
    private MockWebServer mockServer;
    private PawaPayGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        gateway = new PawaPayGateway(
                WebClient.builder(),
                mockServer.url("/").toString(),
                "test-api-key-123"
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    // ─── getGatewayName ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getGatewayName returns PAWAPAY")
    void gatewayName_shouldBePawaPay() {
        assertThat(gateway.getGatewayName()).isEqualTo(GatewayName.PAWAPAY);
    }

    // ─── initiatePayment ──────────────────────────────────────────────────────

    @Test
    @DisplayName("initiatePayment — sends correct V2 body with AIRTEL_OAPI_ZMB provider")
    void initiatePayment_airtel_shouldSendCorrectRequest() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"depositId\":\"f4401bd2-1568-4140-bf2d-eb77d2b2b001\",\"status\":\"ACCEPTED\"}"));

        GatewayPaymentRequest request = GatewayPaymentRequest.builder()
                .orderUuid("f4401bd2-1568-4140-bf2d-eb77d2b2b001")
                .msisdn("0971234567")
                .amount(new BigDecimal("115.00"))
                .currency("ZMW")
                .mobileNetwork(MobileNetwork.AIRTEL)
                .build();

        StepVerifier.create(gateway.initiatePayment(request))
                .assertNext(resp -> assertThat(resp.getGatewayRef()).isEqualTo("f4401bd2-1568-4140-bf2d-eb77d2b2b001"))
                .verifyComplete();

        RecordedRequest recorded = mockServer.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v2/deposits");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-api-key-123");

        String body = recorded.getBody().readUtf8();
        assertThat(body).contains("\"depositId\":\"f4401bd2-1568-4140-bf2d-eb77d2b2b001\"");
        assertThat(body).contains("\"provider\":\"AIRTEL_OAPI_ZMB\"");
        assertThat(body).contains("\"amount\":\"115.00\"");
        assertThat(body).contains("\"currency\":\"ZMW\"");
        // Phone normalised: 0971234567 → 260971234567
        assertThat(body).contains("260971234567");
    }

    @Test
    @DisplayName("initiatePayment — sends MTN_MOMO_ZMB provider for MTN network")
    void initiatePayment_mtn_shouldSendMtnCorrespondent() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"depositId\":\"f4401bd2-1568-4140-bf2d-eb77d2b2b002\",\"status\":\"ACCEPTED\"}"));

        GatewayPaymentRequest request = GatewayPaymentRequest.builder()
                .orderUuid("f4401bd2-1568-4140-bf2d-eb77d2b2b002")
                .msisdn("0761234567")
                .amount(new BigDecimal("50.00"))
                .currency("ZMW")
                .mobileNetwork(MobileNetwork.MTN)
                .build();

        StepVerifier.create(gateway.initiatePayment(request))
                .assertNext(resp -> assertThat(resp.getGatewayRef()).isEqualTo("f4401bd2-1568-4140-bf2d-eb77d2b2b002"))
                .verifyComplete();

        RecordedRequest recorded = mockServer.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertThat(body).contains("\"provider\":\"MTN_MOMO_ZMB\"");
    }

    @Test
    @DisplayName("initiatePayment — PawaPay rejects (non-ACCEPTED status) → PaymentGatewayException")
    void initiatePayment_rejectedByProvider_shouldThrow() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"depositId\":\"ORD-REJ-001\",\"status\":\"REJECTED\",\"failureReason\":{\"failureCode\":\"INVALID_PHONE_NUMBER\",\"failureMessage\":\"Invalid number\"}}"));

        GatewayPaymentRequest request = GatewayPaymentRequest.builder()
                .orderUuid("ORD-REJ-001")
                .msisdn("0971234567")
                .amount(new BigDecimal("50.00"))
                .currency("ZMW")
                .mobileNetwork(MobileNetwork.AIRTEL)
                .build();

        StepVerifier.create(gateway.initiatePayment(request))
                .expectErrorMatches(e -> e.getMessage().contains("PawaPay rejected"))
                .verify();
    }

    @Test
    @DisplayName("initiatePayment — HTTP 500 from PawaPay → error propagated")
    void initiatePayment_httpError_shouldPropagate() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        GatewayPaymentRequest request = GatewayPaymentRequest.builder()
                .orderUuid("ORD-ERR-001")
                .msisdn("0971234567")
                .amount(new BigDecimal("50.00"))
                .currency("ZMW")
                .mobileNetwork(MobileNetwork.AIRTEL)
                .build();

        StepVerifier.create(gateway.initiatePayment(request))
                .expectError()
                .verify();
    }

    // ─── checkPaymentStatus ───────────────────────────────────────────────────

    @Test
    @DisplayName("checkPaymentStatus — COMPLETED maps to SUCCESS")
    void checkStatus_completed_shouldReturnSuccess() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"FOUND\",\"data\":{\"depositId\":\"ORD-001\",\"status\":\"COMPLETED\"}}"));

        StepVerifier.create(gateway.checkPaymentStatus("ORD-001"))
                .assertNext(resp -> {
                    assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.SUCCESS);
                    assertThat(resp.getRawStatus()).isEqualTo("COMPLETED");
                    assertThat(resp.getGatewayRef()).isEqualTo("ORD-001");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("checkPaymentStatus — FAILED maps to FAILED")
    void checkStatus_failed_shouldReturnFailed() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"FOUND\",\"data\":{\"depositId\":\"ORD-002\",\"status\":\"FAILED\"}}")); 

        StepVerifier.create(gateway.checkPaymentStatus("ORD-002"))
                .assertNext(resp -> assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.FAILED))
                .verifyComplete();
    }

    @Test
    @DisplayName("checkPaymentStatus — EXPIRED maps to FAILED")
    void checkStatus_expired_shouldReturnFailed() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"FOUND\",\"data\":{\"depositId\":\"ORD-003\",\"status\":\"EXPIRED\"}}")); 

        StepVerifier.create(gateway.checkPaymentStatus("ORD-003"))
                .assertNext(resp -> assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.FAILED))
                .verifyComplete();
    }

    @Test
    @DisplayName("checkPaymentStatus — SUBMITTED/INITIATED maps to PENDING")
    void checkStatus_submitted_shouldReturnPending() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"FOUND\",\"data\":{\"depositId\":\"ORD-004\",\"status\":\"SUBMITTED\"}}")); 

        StepVerifier.create(gateway.checkPaymentStatus("ORD-004"))
                .assertNext(resp -> assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.PENDING))
                .verifyComplete();
    }

    @Test
    @DisplayName("checkPaymentStatus — HTTP error returns PENDING (don't cancel on flaky status check)")
    void checkStatus_httpError_shouldReturnPending() {
        mockServer.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(gateway.checkPaymentStatus("ORD-005"))
                .assertNext(resp -> {
                    assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.PENDING);
                    assertThat(resp.getRawStatus()).contains("HTTP_ERROR");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("checkPaymentStatus — calls correct GET /v2/deposits/{depositId} endpoint")
    void checkStatus_shouldCallCorrectEndpoint() throws InterruptedException {
        String uuid = "f4401bd2-1568-4140-bf2d-eb77d2b2b639";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"FOUND\",\"data\":{\"depositId\":\"" + uuid + "\",\"status\":\"COMPLETED\"}}")); 

        StepVerifier.create(gateway.checkPaymentStatus(uuid))
                .assertNext(resp -> assertThat(resp.getOutcome()).isEqualTo(GatewayStatusOutcome.SUCCESS))
                .verifyComplete();

        RecordedRequest recorded = mockServer.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v2/deposits/" + uuid);
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-api-key-123");
    }
}
