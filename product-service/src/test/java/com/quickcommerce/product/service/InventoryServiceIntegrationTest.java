package com.quickcommerce.product.service;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.dto.AddStockRequest;
import com.quickcommerce.product.dto.ReserveStockRequest;
import com.quickcommerce.product.exception.InsufficientStockException;
import com.quickcommerce.product.exception.InventoryNotFoundException;
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
 * Integration tests for InventoryService in Product Service
 */
class InventoryServiceIntegrationTest extends BaseContainerTest {

    @Autowired
    private InventoryService inventoryService;

    @BeforeEach
    void setUpTest() {
        // Create test data
        createTestInventoryItem("SKU001", 101L, 1L, 100);
        createTestInventoryItem("SKU002", 102L, 1L, 50);
        createTestInventoryItem("SKU003", 103L, 1L, 0); // Out of stock
    }

    @Test
    @DisplayName("Should get inventory item by SKU successfully")
    void shouldGetInventoryBySku() {
        Mono<InventoryItem> result = inventoryService.getInventoryBySku("SKU001");

        StepVerifier.create(result)
                .assertNext(item -> {
                    assertThat(item.getSku()).isEqualTo("SKU001");
                    assertThat(item.getCurrentStock()).isEqualTo(100);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reserve stock successfully")
    void shouldReserveStockSuccessfully() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(10)
                .customerId(123L)
                .orderId("ORDER123")
                .build();

        var result = inventoryService.reserveStock(request);

        StepVerifier.create(result)
                .assertNext(reservation -> {
                    assertThat(reservation.getSku()).isEqualTo("SKU001");
                    assertThat(reservation.getQuantity()).isEqualTo(10);
                    assertThat(reservation.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getReservedStock()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw InsufficientStockException when not enough stock")
    void shouldThrowInsufficientStockException() {
        ReserveStockRequest request = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(150)
                .customerId(123L)
                .orderId("ORDER123")
                .build();

        StepVerifier.create(inventoryService.reserveStock(request))
                .expectError(InsufficientStockException.class)
                .verify();
    }

    @Test
    @DisplayName("Should add stock successfully")
    void shouldAddStockSuccessfully() {
        AddStockRequest request = AddStockRequest.builder()
                .sku("SKU001")
                .quantity(25)
                .build();

        var result = inventoryService.addStock(request);

        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(125);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should prevent overselling under high concurrency")
    void shouldPreventOversellingUnderHighConcurrency() {
        createTestInventoryItem("SKU_CONC", 999L, 1L, 5);

        Flux<String> results = Flux.range(1, 10)
                .flatMap(i -> {
                    ReserveStockRequest req = ReserveStockRequest.builder()
                            .sku("SKU_CONC")
                            .quantity(1)
                            .customerId((long) i)
                            .orderId("ORDER_" + i)
                            .build();

                    return inventoryService.reserveStock(req)
                            .map(r -> "SUCCESS")
                            .onErrorReturn(InsufficientStockException.class, "FAILED");
                });

        StepVerifier.create(results.collectList())
                .assertNext(list -> {
                    long successCount = list.stream().filter(s -> s.equals("SUCCESS")).count();
                    assertThat(successCount).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should confirm reservation successfully")
    void shouldConfirmReservationSuccessfully() {
        ReserveStockRequest reserveRequest = ReserveStockRequest.builder()
                .sku("SKU001")
                .quantity(10)
                .customerId(123L)
                .orderId("ORDER123")
                .build();

        var reservation = inventoryService.reserveStock(reserveRequest).block();

        var result = inventoryService.confirmReservation(reservation.getReservationId());

        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(inventoryService.getInventoryBySku("SKU001"))
                .assertNext(item -> {
                    assertThat(item.getCurrentStock()).isEqualTo(90);
                    assertThat(item.getReservedStock()).isEqualTo(0);
                })
                .verifyComplete();
    }
}
