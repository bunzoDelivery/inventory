package com.quickcommerce.order;

import com.quickcommerce.order.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Order Service
 * Tests complete order workflows end-to-end with real database
 */
@AutoConfigureWebTestClient
class OrderServiceIntegrationTest extends BaseIntegrationTest {

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

    // ========== Order Creation Tests ==========

    @Test
    @DisplayName("Create COD order - should confirm immediately and commit stock")
    void createCodOrder_shouldConfirmImmediately() throws JsonProcessingException {
        // Given
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200)); // confirm

        // When
        OrderResponse response = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_001", "COD"))
                .exchange()
                // Then
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CONFIRMED");
        assertThat(response.getPaymentStatus()).isEqualTo("COD_PENDING");
        assertThat(response.getPaymentMethod()).isEqualTo("COD");
        assertThat(response.getItemsTotal()).isEqualByComparingTo("50.00");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo("15.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("65.00");
        assertThat(response.getDelivery()).isNotNull();
        assertThat(response.getDelivery().getAddress()).contains("Cairo Road");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getCreatedAt()).isNotNull();

        // Verify stock was confirmed (2 calls: reserve + confirm)
        assertThat(mockInventoryService.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Create digital payment order - should be pending payment and reserve stock")
    void createDigitalPaymentOrder_shouldBePendingPayment() throws JsonProcessingException {
        // Given
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        // When
        OrderResponse response = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_002", "AIRTEL_MONEY"))
                .exchange()
                // Then
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(response.getStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.getPaymentStatus()).isEqualTo("PENDING");
        assertThat(response.getMessage()).contains("proceed to payment");
        
        // Only reserve called, not confirm
        assertThat(mockInventoryService.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Create order with multiple items - should calculate correct totals")
    void createOrderWithMultipleItems_shouldCalculateCorrectTotals() throws JsonProcessingException {
        // Given
        CreateOrderRequest request = buildMultiItemOrderRequest("CUST_003", "MTN_MONEY", 3);

        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        mockPrice("SKU1", 50.0),
                        mockPrice("SKU2", 75.0),
                        mockPrice("SKU3", 100.0)
                )))
                .addHeader("Content-Type", "application/json"));

        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        mockReservation("SKU1", 1),
                        mockReservation("SKU2", 2),
                        mockReservation("SKU3", 3)
                )))
                .addHeader("Content-Type", "application/json"));

        // When
        OrderResponse response = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(response.getItems()).hasSize(3);
        // SKU1: 50*1 = 50, SKU2: 75*2 = 150, SKU3: 100*3 = 300 = 500 total
        assertThat(response.getItemsTotal()).isEqualByComparingTo("500.00");
        assertThat(response.getDeliveryFee()).isEqualByComparingTo("15.00");
        assertThat(response.getGrandTotal()).isEqualByComparingTo("515.00");
    }

    @Test
    @DisplayName("Create order with idempotency key - duplicate requests should return same order")
    void createOrder_withIdempotencyKey_shouldReturnSameOrder() throws JsonProcessingException {
        // Given
        String idempotencyKey = "TEST_IDEM_001";
        
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        // When - First request
        OrderResponse firstResponse = webTestClient.post().uri("/api/v1/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_004", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // When - Second request with same key (no additional mocks needed)
        OrderResponse secondResponse = webTestClient.post().uri("/api/v1/orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_004", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(firstResponse.getOrderId()).isEqualTo(secondResponse.getOrderId());
        assertThat(mockProductService.getRequestCount()).isEqualTo(1); // Called only once
        assertThat(mockInventoryService.getRequestCount()).isEqualTo(1);
    }

    // ========== Validation Tests ==========

    @Test
    @DisplayName("Create order with invalid quantity - should return 400")
    void createOrder_invalidQuantity_shouldReturnBadRequest() {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerId("CUST_005")
                .storeId(1L)
                .paymentMethod("COD")
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 0))) // Invalid
                .delivery(CreateOrderRequest.DeliveryRequest.builder()
                        .latitude(-15.4167).longitude(28.2833)
                        .address("Address").phone("0977123456")
                        .build())
                .build();

        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(error -> assertThat(error.get("errors")).isNotNull());
    }

    @Test
    @DisplayName("Create order without delivery address - should return 400")
    void createOrder_missingDelivery_shouldReturnBadRequest() {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerId("CUST_006")
                .storeId(1L)
                .paymentMethod("COD")
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 1)))
                .build(); // Missing delivery

        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("Create order with invalid Zambian phone - should return 400")
    void createOrder_invalidPhone_shouldReturnBadRequest() {
        CreateOrderRequest invalidRequest = CreateOrderRequest.builder()
                .customerId("CUST_007")
                .storeId(1L)
                .paymentMethod("COD")
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 1)))
                .delivery(CreateOrderRequest.DeliveryRequest.builder()
                        .latitude(-15.4167).longitude(28.2833)
                        .address("Address").phone("123") // Invalid
                        .build())
                .build();

        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ========== Payment Tests ==========

    @Test
    @DisplayName("Mock payment - should confirm pending order")
    void mockPayment_shouldConfirmOrder() throws JsonProcessingException {
        // Given - Create pending order first
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 75.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse pendingOrder = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_008", "MTN_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(pendingOrder.getStatus()).isEqualTo("PENDING_PAYMENT");

        // Mock payment confirmation
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200)); // confirm

        // When
        OrderResponse paidOrder = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/pay-mock", pendingOrder.getOrderId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(paidOrder.getStatus()).isEqualTo("CONFIRMED");
        assertThat(paidOrder.getPaymentStatus()).isEqualTo("PAID");
        assertThat(mockInventoryService.getRequestCount()).isEqualTo(2); // reserve + confirm
    }

    @Test
    @DisplayName("Mock payment on COD order - should fail")
    void mockPayment_codOrder_shouldFail() throws JsonProcessingException {
        // Given - Create COD order
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200)); // confirm

        OrderResponse codOrder = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_009", "COD"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // When/Then - Try to pay COD order online
        webTestClient.post().uri("/api/v1/orders/{orderUuid}/pay-mock", codOrder.getOrderId())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(error -> assertThat(error.get("error").toString()).contains("COD"));
    }

    // ========== Query Tests ==========

    @Test
    @DisplayName("Get order by UUID - should return order details")
    void getOrder_shouldReturnDetails() throws JsonProcessingException {
        // Given - Create order
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse createdOrder = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_010", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // When
        OrderResponse retrieved = webTestClient.get()
                .uri("/api/v1/orders/{orderUuid}", createdOrder.getOrderId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(retrieved.getOrderId()).isEqualTo(createdOrder.getOrderId());
        assertThat(retrieved.getDelivery()).isNotNull();
        assertThat(retrieved.getDelivery().getAddress()).contains("Cairo Road");
        assertThat(retrieved.getDelivery().getNotes()).isEqualTo("Gate code: 5678");
    }

    @Test
    @DisplayName("Get non-existent order - should return 404")
    void getOrder_notFound_shouldReturn404() {
        webTestClient.get().uri("/api/v1/orders/NONEXISTENT")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("Get customer orders - should return all orders for customer")
    void getCustomerOrders_shouldReturnCustomerOrders() throws JsonProcessingException {
        String customerId = "CUST_011";

        // Given - Create 3 orders for same customer
        for (int i = 0; i < 3; i++) {
            mockProductService.enqueue(new MockResponse()
                    .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                    .addHeader("Content-Type", "application/json"));
            mockInventoryService.enqueue(new MockResponse()
                    .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                    .addHeader("Content-Type", "application/json"));

            webTestClient.post().uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildOrderRequest(customerId, "AIRTEL_MONEY"))
                    .exchange()
                    .expectStatus().isCreated();
        }

        // When
        List<OrderResponse> orders = webTestClient.get()
                .uri("/api/v1/orders/customer/{customerId}", customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(orders).hasSize(3);
        assertThat(orders).allMatch(o -> o.getOrderId() != null);
    }

    @Test
    @DisplayName("Get store orders - should return orders for store")
    void getStoreOrders_shouldReturnStoreOrders() throws JsonProcessingException {
        // Given - Create 2 orders
        for (int i = 0; i < 2; i++) {
            mockProductService.enqueue(new MockResponse()
                    .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                    .addHeader("Content-Type", "application/json"));
            mockInventoryService.enqueue(new MockResponse()
                    .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                    .addHeader("Content-Type", "application/json"));
            mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

            webTestClient.post().uri("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildOrderRequest("CUST_" + i, "COD"))
                    .exchange()
                    .expectStatus().isCreated();
        }

        // When
        List<OrderResponse> orders = webTestClient.get()
                .uri("/api/v1/orders/store/1")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(orders).hasSize(2);
    }

    @Test
    @DisplayName("Get store orders by status - should filter by status")
    void getStoreOrdersByStatus_shouldFilterCorrectly() throws JsonProcessingException {
        // Given - Create COD (CONFIRMED) and digital (PENDING_PAYMENT) orders
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));
        
        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_012", "COD"))
                .exchange().expectStatus().isCreated();

        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        
        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_013", "AIRTEL_MONEY"))
                .exchange().expectStatus().isCreated();

        // When - Get only CONFIRMED orders
        List<OrderResponse> confirmedOrders = webTestClient.get()
                .uri("/api/v1/orders/store/1?status=CONFIRMED")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(confirmedOrders).hasSize(1);
        assertThat(confirmedOrders.get(0).getStatus()).isEqualTo("CONFIRMED");
    }

    // ========== Cancellation Tests ==========

    @Test
    @DisplayName("Cancel pending order - should cancel and release stock")
    void cancelOrder_shouldCancelSuccessfully() throws JsonProcessingException {
        // Given
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse order = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_014", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Mock cancel reservation
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        CancelOrderRequest cancelRequest = new CancelOrderRequest("Changed my mind");

        // When
        OrderResponse cancelled = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/cancel", order.getOrderId())
                .header("Customer-Id", "CUST_014")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cancelRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(mockInventoryService.getRequestCount()).isEqualTo(2); // reserve + cancel
    }

    @Test
    @DisplayName("Cancel order by wrong customer - should fail")
    void cancelOrder_wrongCustomer_shouldFail() throws JsonProcessingException {
        // Given
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 100.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse order = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_015", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // When/Then
        webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/cancel", order.getOrderId())
                .header("Customer-Id", "WRONG_CUSTOMER")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CancelOrderRequest("Reason"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ========== Fulfillment Tests ==========

    @Test
    @DisplayName("Update status CONFIRMED → PACKING - should succeed")
    void updateStatus_confirmedToPacking_shouldSucceed() throws JsonProcessingException {
        // Given - Create COD order (auto-confirmed)
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200)); // confirm

        OrderResponse confirmed = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_016", "COD"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        UpdateOrderStatusRequest updateRequest = new UpdateOrderStatusRequest("PACKING", "Started packing");

        // When
        OrderResponse packing = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/status", confirmed.getOrderId())
                .header("Actor-Id", "STORE_STAFF_001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // Then
        assertThat(packing.getStatus()).isEqualTo("PACKING");
    }

    @Test
    @DisplayName("Update status with invalid transition - should fail")
    void updateStatus_invalidTransition_shouldFail() throws JsonProcessingException {
        // Given - Create pending payment order
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));

        OrderResponse pending = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_017", "AIRTEL_MONEY"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        // When/Then - Try invalid transition PENDING_PAYMENT → PACKING
        UpdateOrderStatusRequest updateRequest = new UpdateOrderStatusRequest("PACKING", null);
        
        webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/status", pending.getOrderId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .value(error -> assertThat(error.get("error").toString()).contains("transition"));
    }

    @Test
    @DisplayName("Complete order flow: CONFIRMED → PACKING → OUT_FOR_DELIVERY → DELIVERED")
    void completeOrderFlow_shouldTransitionThroughAllStates() throws JsonProcessingException {
        // Given - Create COD order
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockPrice("SKU1", 50.0))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(mockReservation("SKU1", 1))))
                .addHeader("Content-Type", "application/json"));
        mockInventoryService.enqueue(new MockResponse().setResponseCode(200));

        OrderResponse order = webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildOrderRequest("CUST_018", "COD"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order.getStatus()).isEqualTo("CONFIRMED");

        // → PACKING
        order = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/status", order.getOrderId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateOrderStatusRequest("PACKING", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order.getStatus()).isEqualTo("PACKING");

        // → OUT_FOR_DELIVERY
        order = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/status", order.getOrderId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateOrderStatusRequest("OUT_FOR_DELIVERY", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order.getStatus()).isEqualTo("OUT_FOR_DELIVERY");

        // → DELIVERED
        order = webTestClient.post()
                .uri("/api/v1/orders/{orderUuid}/status", order.getOrderId())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateOrderStatusRequest("DELIVERED", null))
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order.getStatus()).isEqualTo("DELIVERED");
    }
}
