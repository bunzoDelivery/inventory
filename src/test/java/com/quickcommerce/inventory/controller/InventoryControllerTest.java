package com.quickcommerce.inventory.controller;

import com.quickcommerce.inventory.domain.InventoryItem;
import com.quickcommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void getInventoryBySku_shouldReturnInventoryItem() {
        // Given
        String sku = "SKU001";
        InventoryItem inventoryItem = InventoryItem.builder()
                .id(1L)
                .sku(sku)
                .productId(1001L)
                .storeId(1L)
                .currentStock(50)
                .reservedStock(0)
                .safetyStock(10)
                .maxStock(100)
                .unitCost(new BigDecimal("15.99"))
                .lastUpdated(LocalDateTime.now())
                .build();

        when(inventoryService.getInventoryBySku(sku)).thenReturn(Mono.just(inventoryItem));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/inventory/sku/{sku}", sku)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(sku)
                .jsonPath("$.currentStock").isEqualTo(50)
                .jsonPath("$.availableStock").isEqualTo(50);
    }

    @Test
    void getInventoryBySku_shouldReturnNotFound() {
        // Given
        String sku = "NONEXISTENT";
        when(inventoryService.getInventoryBySku(sku)).thenReturn(Mono.error(new RuntimeException("SKU not found")));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/inventory/sku/{sku}", sku)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void health_shouldReturnOk() {
        webTestClient.get()
                .uri("/api/v1/inventory/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("Inventory Service is healthy");
    }
}
