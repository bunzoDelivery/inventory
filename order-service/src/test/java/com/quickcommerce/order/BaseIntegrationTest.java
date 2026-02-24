package com.quickcommerce.order;

import com.quickcommerce.order.dto.CreateOrderRequest;
import com.quickcommerce.order.dto.ProductPriceResponse;
import com.quickcommerce.order.dto.StockReservationResponse;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderItemRepository;
import com.quickcommerce.order.repository.OrderRepository;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Base class for integration tests with Testcontainers MySQL setup
 * and MockWebServer for external services.
 * 
 * Provides:
 * - MySQL container with automatic Flyway migrations
 * - MockWebServer for product-service and inventory-service
 * - Helper methods for creating test data
 * - Repository access for test data cleanup
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("inventory")
            .withUsername("root")
            .withPassword("root")
            .withReuse(true); // Reuse container across test classes for speed

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected OrderItemRepository orderItemRepository;

    @Autowired
    protected OrderEventRepository orderEventRepository;

    protected static MockWebServer mockProductService;
    protected static MockWebServer mockInventoryService;
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Database properties
        registry.add("spring.r2dbc.url", () -> 
                "r2dbc:mysql://" + mysql.getHost() + ":" + mysql.getFirstMappedPort() + "/inventory");
        registry.add("spring.r2dbc.username", mysql::getUsername);
        registry.add("spring.r2dbc.password", mysql::getPassword);
        registry.add("spring.flyway.url", mysql::getJdbcUrl);
        registry.add("spring.flyway.user", mysql::getUsername);
        registry.add("spring.flyway.password", mysql::getPassword);
        
        // Disable scheduling in tests
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    protected static void startMockServers() throws IOException {
        if (mockProductService == null) {
            mockProductService = new MockWebServer();
            mockProductService.start();
        }
        if (mockInventoryService == null) {
            mockInventoryService = new MockWebServer();
            mockInventoryService.start();
        }
    }

    protected static void stopMockServers() throws IOException {
        if (mockProductService != null) {
            mockProductService.shutdown();
            mockProductService = null;
        }
        if (mockInventoryService != null) {
            mockInventoryService.shutdown();
            mockInventoryService = null;
        }
    }

    /**
     * Clean all test data from database
     */
    protected void cleanDatabase() {
        orderEventRepository.deleteAll().block();
        orderItemRepository.deleteAll().block();
        orderRepository.deleteAll().block();
    }

    /**
     * Helper: Build a standard order request for testing
     */
    protected CreateOrderRequest buildOrderRequest(String customerId, String paymentMethod) {
        return CreateOrderRequest.builder()
                .customerId(customerId)
                .storeId(1L)
                .paymentMethod(paymentMethod)
                .items(List.of(new CreateOrderRequest.OrderItemRequest("SKU1", 1)))
                .delivery(CreateOrderRequest.DeliveryRequest.builder()
                        .latitude(-15.4167)
                        .longitude(28.2833)
                        .address("Plot 1234, Cairo Road, Lusaka")
                        .phone("0977123456")
                        .notes("Gate code: 5678")
                        .build())
                .build();
    }

    /**
     * Helper: Build order request with multiple items
     */
    protected CreateOrderRequest buildMultiItemOrderRequest(String customerId, String paymentMethod, int itemCount) {
        List<CreateOrderRequest.OrderItemRequest> items = new java.util.ArrayList<>();
        for (int i = 1; i <= itemCount; i++) {
            items.add(new CreateOrderRequest.OrderItemRequest("SKU" + i, i));
        }

        return CreateOrderRequest.builder()
                .customerId(customerId)
                .storeId(1L)
                .paymentMethod(paymentMethod)
                .items(items)
                .delivery(CreateOrderRequest.DeliveryRequest.builder()
                        .latitude(-15.4167)
                        .longitude(28.2833)
                        .address("Test Address")
                        .phone("0977123456")
                        .build())
                .build();
    }

    /**
     * Helper: Create mock product price response
     */
    protected ProductPriceResponse mockPrice(String sku, double price) {
        return ProductPriceResponse.builder()
                .sku(sku)
                .basePrice(BigDecimal.valueOf(price))
                .build();
    }

    /**
     * Helper: Create mock stock reservation response
     */
    protected StockReservationResponse mockReservation(String sku, int quantity) {
        return new StockReservationResponse(
                "RES_" + sku, 
                sku, 
                quantity, 
                "ACTIVE"
        );
    }
}
