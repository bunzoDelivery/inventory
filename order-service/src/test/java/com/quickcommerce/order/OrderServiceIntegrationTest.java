package com.quickcommerce.order;

import com.quickcommerce.order.dto.CreateOrderRequest;
import com.quickcommerce.order.dto.OrderResponse;
import com.quickcommerce.order.dto.ProductPriceResponse;
import com.quickcommerce.order.dto.StockReservationResponse;
import com.quickcommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@AutoConfigureWebTestClient
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OrderRepository orderRepository;

    private static MockWebServer mockProductService;
    private static MockWebServer mockInventoryService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void setUp() throws IOException {
        mockProductService = new MockWebServer();
        mockProductService.start();
        
        mockInventoryService = new MockWebServer();
        mockInventoryService.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockProductService.shutdown();
        mockInventoryService.shutdown();
    }

    @DynamicPropertySource
    static void configureClientProperties(DynamicPropertyRegistry registry) {
        registry.add("client.product-service.url", () -> mockProductService.url("/").toString());
        registry.add("client.inventory-service.url", () -> mockInventoryService.url("/").toString());
    }
    
    @BeforeEach
    void clearDb() {
        orderRepository.deleteAll().block();
    }

    /*@Test
    void createOrder_shouldSucceed() throws JsonProcessingException {
        // Mock Catalog Response
        mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        new ProductPriceResponse("SKU1", BigDecimal.valueOf(100.0), true)
                )))
                .addHeader("Content-Type", "application/json"));

        // Mock Inventory Reserve Response
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        new StockReservationResponse("RES123", "SKU1", 1, "ACTIVE")
                )))
                .addHeader("Content-Type", "application/json"));
                
        // Mock Inventory Confirm Response (if needed later or different flow)
        // For Digital Order, we only Reserve initially.

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(1L)
                .storeId("STORE1")
                .paymentMethod("AIRTEL_MONEY")
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 1)))
                .build();

        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(response -> {
                    assert response.getStatus().equals("PENDING_PAYMENT");
                    assert response.getTotalAmount().compareTo(BigDecimal.valueOf(100.0)) == 0;
                });
    }
    
    @Test
    void createCodOrder_shouldConfirmImmediately() throws JsonProcessingException {
        // Mock Catalog
         mockProductService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        new ProductPriceResponse("SKU1", BigDecimal.valueOf(50.0), true)
                )))
                .addHeader("Content-Type", "application/json"));

        // Mock Inventory Reserve
        mockInventoryService.enqueue(new MockResponse()
                .setBody(objectMapper.writeValueAsString(List.of(
                        new StockReservationResponse("RES123", "SKU1", 1, "ACTIVE")
                )))
                .addHeader("Content-Type", "application/json"));

        // Mock Inventory Confirm
        mockInventoryService.enqueue(new MockResponse()
                .setResponseCode(200));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId(2L)
                .storeId("STORE1")
                .paymentMethod("COD")
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 1)))
                .build();

        webTestClient.post().uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .value(response -> {
                    assert response.getStatus().equals("CONFIRMED");
                });
    }*/
}
