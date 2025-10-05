package com.quickcommerce.inventory.service;

import com.quickcommerce.inventory.BaseContainerTest;
import com.quickcommerce.inventory.domain.InventoryItem;
import com.quickcommerce.inventory.domain.StockReservation;
import com.quickcommerce.inventory.dto.AddStockRequest;
import com.quickcommerce.inventory.dto.InventoryAvailabilityRequest;
import com.quickcommerce.inventory.dto.ReserveStockRequest;
import com.quickcommerce.inventory.exception.InsufficientStockException;
import com.quickcommerce.inventory.exception.InventoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for InventoryService using Testcontainers
 */
class InventoryServiceIntegrationTest extends BaseContainerTest {

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void setUpTest() {
        // Create test data
        createTestInventoryItem("SKU001", 101L, 1L, 100, 10);
        createTestInventoryItem("SKU002", 102L, 1L, 50, 5);
        createTestInventoryItem("SKU003", 103L, 1L, 0, 5); // Out of stock
    }

    @Test
    @DisplayName("Should get inventory item by SKU successfully")
    void shouldGetInventoryBySku() {
        // When
        Mono<InventoryItem> result = inventoryService.getInventoryBySku("SKU001");

        // Then
        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.getSku()).isEqualTo("SKU001");
                    assertThat(item.getProductId()).isEqualTo(101L);
                    assertThat(item.getStoreId()).isEqualTo(1L);
                    assertThat(item.getCurrentStock()).isEqualTo(100);
                    assertThat(item.getSafetyStock()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw InventoryNotFoundException when inventory item not found")
    void shouldReturnEmptyWhenInventoryNotFound() {
        // When
        Mono<InventoryItem> result = inventoryService.getInventoryBySku("NON_EXISTENT_SKU");

        // Then
        StepVerifier.create(result)
                .expectError(InventoryNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void shouldReserveStockSuccessfully() {
        // Given
        ReserveStockRequest request = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(10)
                .customerId("123")
                .orderId("ORDER123")
                .build();

        // When
        var result = inventoryService.reserveStock(request);

        // Then
        StepVerifier.create(result)
                .assertNext(reservation -> {
                    assertThat(reservation.getSku()).isEqualTo("SKU001");
                    assertThat(reservation.getQuantity()).isEqualTo(10);
                    assertThat(reservation.getOrderId()).isEqualTo("ORDER123");
                    assertThat(reservation.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        // Verify inventory was updated
        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(100);
                    assertThat(item.getReservedStock()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when not enough stock")
    void shouldThrowInsufficientStockException() {
        // Given
        ReserveStockRequest request = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(150) // More than available (100)
                .customerId("123")
                .orderId("ORDER123")
                .build();

        // When & Then
        StepVerifier.create(inventoryService.reserveStock(request))
                .expectError(InsufficientStockException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw InventoryNotFoundException for non-existent SKU")
    void shouldThrowInventoryNotFoundException() {
        // Given
        ReserveStockRequest request = ReserveStockRequest.builder()
                .sku("NON_EXISTENT_SKU")
                .quantity(10)
                .customerId("123")
                .orderId("ORDER123")
                .build();

        // When & Then
        StepVerifier.create(inventoryService.reserveStock(request))
                .expectError(InventoryNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should confirm reservation successfully")
    void shouldConfirmReservationSuccessfully() {
        // Given - First reserve stock
        ReserveStockRequest reserveRequest = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(10)
                .customerId("123")
                .orderId("ORDER123")
                .build();

        var reservation = inventoryService.reserveStock(reserveRequest).block();

        // When - Confirm reservation
        var result = inventoryService.confirmReservation(reservation.getReservationId());

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        // Verify inventory was updated (stock reduced)
        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(90); // 100 - 10
                    assertThat(item.getReservedStock()).isEqualTo(0); // Reserved stock released
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cancel reservation successfully")
    void shouldCancelReservationSuccessfully() {
        // Given - First reserve stock
        ReserveStockRequest reserveRequest = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(10)
                .customerId("123")
                .orderId("ORDER123")
                .build();

        var reservation = inventoryService.reserveStock(reserveRequest).block();

        // When - Cancel reservation
        var result = inventoryService.cancelReservation(reservation.getReservationId());

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        // Verify inventory was updated (reserved stock released)
        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(100); // Unchanged
                    assertThat(item.getReservedStock()).isEqualTo(0); // Reserved stock released
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should add stock successfully")
    void shouldAddStockSuccessfully() {
        // Given
        AddStockRequest request = AddStockRequest.builder()
                .sku("SKU001")
                .quantity(25)
                .build();

        // When
        var result = inventoryService.addStock(request);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        // Verify inventory was updated
        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(125); // 100 + 25
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check inventory availability for multiple SKUs")
    void shouldCheckInventoryAvailabilityForMultipleSkus() {
        // Given
        InventoryAvailabilityRequest request = InventoryAvailabilityRequest.builder()
                .storeId(1L)
                .skus(List.of("SKU001", "SKU002", "SKU003", "NON_EXISTENT_SKU"))
                .build();

        // When
        var result = inventoryService.checkInventoryAvailability(request.getStoreId(), request.getSkus());

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStoreId()).isEqualTo(1L);
                    assertThat(response.getProducts()).hasSize(3); // Only existing SKUs

                    var product1 = response.getProducts().stream()
                            .filter(p -> "SKU001".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(product1.getCurrentStock()).isEqualTo(100);
                    assertThat(product1.getAvailableStock()).isEqualTo(100);
                    assertThat(product1.getInStock()).isTrue();
                    assertThat(product1.getLowStock()).isFalse();

                    var product2 = response.getProducts().stream()
                            .filter(p -> "SKU002".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(product2.getCurrentStock()).isEqualTo(50);
                    assertThat(product2.getAvailableStock()).isEqualTo(50);
                    assertThat(product2.getInStock()).isTrue();
                    assertThat(product2.getLowStock()).isFalse();

                    var product3 = response.getProducts().stream()
                            .filter(p -> "SKU003".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(product3.getCurrentStock()).isEqualTo(0);
                    assertThat(product3.getAvailableStock()).isEqualTo(0);
                    assertThat(product3.getInStock()).isFalse();
                    assertThat(product3.getLowStock()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get low stock items")
    void shouldGetLowStockItems() {
        // Given - SKU003 has 0 stock, which is <= safety stock of 5

        // When
        var result = inventoryService.getLowStockItems(1L);

        // Then
        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.getSku()).isEqualTo("SKU003");
                    assertThat(item.getCurrentStock()).isEqualTo(0);
                    assertThat(item.getSafetyStock()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle concurrent stock reservations correctly")
    void shouldHandleConcurrentStockReservations() {
        // Given
        ReserveStockRequest request1 = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(40)
                .customerId("123")
                .orderId("ORDER1")
                .build();

        ReserveStockRequest request2 = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(40)
                .customerId("456")
                .orderId("ORDER2")
                .build();

        // When - Reserve stock concurrently
        var reservation1 = inventoryService.reserveStock(request1);
        var reservation2 = inventoryService.reserveStock(request2);

        // Then - Both should succeed (total 80, available 100)
        StepVerifier.create(Mono.when(reservation1, reservation2))
                .verifyComplete();

        // Verify final inventory state
        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(100);
                    assertThat(item.getReservedStock()).isEqualTo(80); // 40 + 40
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RACE CONDITION TEST: Should prevent overselling when 3 customers try to buy 1 item with only 1 available")
    void shouldPreventOversellingWithRaceCondition_OneItemThreeCustomers() {
        // Given - Create item with ONLY 1 available
        createTestInventoryItem("SKU_RACE_1", 999L, 1L, 1, 0); // current=1, reserved=0

        ReserveStockRequest req1 = ReserveStockRequest.builder()
                .sku("SKU_RACE_1")
                .quantity(1)
                .customerId("111")
                .orderId("ORDER_A")
                .build();

        ReserveStockRequest req2 = ReserveStockRequest.builder()
                .sku("SKU_RACE_1")
                .quantity(1)
                .customerId("222")
                .orderId("ORDER_B")
                .build();

        ReserveStockRequest req3 = ReserveStockRequest.builder()
                .sku("SKU_RACE_1")
                .quantity(1)
                .customerId("333")
                .orderId("ORDER_C")
                .build();

        // When - All 3 customers try to reserve simultaneously
        var result1 = inventoryService.reserveStock(req1);
        var result2 = inventoryService.reserveStock(req2);
        var result3 = inventoryService.reserveStock(req3);

        // Merge all results and collect successes and failures
        var results = Flux.merge(
                result1.map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED"),
                result2.map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED"),
                result3.map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED")
        ).collectList();

        // Then - Exactly 1 should succeed, 2 should fail
        StepVerifier.create(results)
                .assertNext(list -> {
                    long successCount = list.stream().filter(s -> s.equals("SUCCESS")).count();
                    long failedCount = list.stream().filter(s -> s.equals("FAILED")).count();

                    assertThat(successCount).isEqualTo(1); // Only 1 succeeded
                    assertThat(failedCount).isEqualTo(2);  // 2 failed due to insufficient stock
                })
                .verifyComplete();

        // Verify final state: reserved_stock should be exactly 1 (not 2 or 3)
        StepVerifier.create(inventoryService.getInventoryBySku("SKU_RACE_1"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(1);
                    assertThat(item.getReservedStock()).isEqualTo(1); // CRITICAL: Must be 1, not 2 or 3
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RACE CONDITION TEST: Should handle 3 customers trying to buy 1 each with only 2 available")
    void shouldPreventOversellingWithRaceCondition_TwoItemsThreeCustomers() {
        // Given - Create item with ONLY 2 available
        createTestInventoryItem("SKU_RACE_2", 998L, 1L, 2, 0); // current=2, reserved=0

        ReserveStockRequest req1 = ReserveStockRequest.builder()
                .sku("SKU_RACE_2")
                .quantity(1)
                .customerId("111")
                .orderId("ORDER_X")
                .build();

        ReserveStockRequest req2 = ReserveStockRequest.builder()
                .sku("SKU_RACE_2")
                .quantity(1)
                .customerId("222")
                .orderId("ORDER_Y")
                .build();

        ReserveStockRequest req3 = ReserveStockRequest.builder()
                .sku("SKU_RACE_2")
                .quantity(1)
                .customerId("333")
                .orderId("ORDER_Z")
                .build();

        // When - All 3 try to reserve simultaneously
        var results = Flux.merge(
                inventoryService.reserveStock(req1).map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED"),
                inventoryService.reserveStock(req2).map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED"),
                inventoryService.reserveStock(req3).map(r -> "SUCCESS").onErrorReturn(InsufficientStockException.class, "FAILED")
        ).collectList();

        // Then - Exactly 2 succeed, 1 fails
        StepVerifier.create(results)
                .assertNext(list -> {
                    long successCount = list.stream().filter(s -> s.equals("SUCCESS")).count();
                    long failedCount = list.stream().filter(s -> s.equals("FAILED")).count();

                    assertThat(successCount).isEqualTo(2); // 2 succeeded
                    assertThat(failedCount).isEqualTo(1);  // 1 failed
                })
                .verifyComplete();

        // Verify final state: reserved_stock should be exactly 2 (not 3)
        StepVerifier.create(inventoryService.getInventoryBySku("SKU_RACE_2"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(2);
                    assertThat(item.getReservedStock()).isEqualTo(2); // CRITICAL: Must be 2, not 3
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RACE CONDITION TEST: Should handle high concurrency - 10 customers, 5 items available")
    void shouldPreventOversellingUnderHighConcurrency() {
        // Given - Create item with 5 available
        createTestInventoryItem("SKU_RACE_5", 997L, 1L, 5, 0); // current=5, reserved=0

        // When - 10 customers try to reserve 1 each simultaneously
        var requests = Flux.range(1, 10)
                .flatMap(i -> {
                    ReserveStockRequest req = ReserveStockRequest.builder()
                            .sku("SKU_RACE_5")
                            .quantity(1)
                            .customerId(String.valueOf(i))
                            .orderId("ORDER_" + i)
                            .build();

                    return inventoryService.reserveStock(req)
                            .map(r -> "SUCCESS")
                            .onErrorReturn(InsufficientStockException.class, "FAILED");
                })
                .collectList();

        // Then - Exactly 5 succeed, 5 fail
        StepVerifier.create(requests)
                .assertNext(list -> {
                    long successCount = list.stream().filter(s -> s.equals("SUCCESS")).count();
                    long failedCount = list.stream().filter(s -> s.equals("FAILED")).count();

                    assertThat(successCount).isEqualTo(5); // Exactly 5 succeeded
                    assertThat(failedCount).isEqualTo(5);  // Exactly 5 failed
                })
                .verifyComplete();

        // Verify final state
        StepVerifier.create(inventoryService.getInventoryBySku("SKU_RACE_5"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(5);
                    assertThat(item.getReservedStock()).isEqualTo(5); // CRITICAL: Exactly 5, no overselling
                })
                .verifyComplete();
    }
}
