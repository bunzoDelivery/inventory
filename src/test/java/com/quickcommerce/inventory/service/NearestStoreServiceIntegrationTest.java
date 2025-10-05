package com.quickcommerce.inventory.service;

import com.quickcommerce.inventory.BaseContainerTest;
import com.quickcommerce.inventory.domain.Store;
import com.quickcommerce.inventory.dto.NearestStoreResponse;
import com.quickcommerce.inventory.exception.InventoryNotFoundException;
import com.quickcommerce.inventory.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for nearest store functionality
 */
class NearestStoreServiceIntegrationTest extends BaseContainerTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void setUpTest() {
        // Create test store
        createTestStore(1L, "Lusaka Dark Store 1", "Plot 5, Great East Road", -15.3875, 28.3228, 5, true);

        // Create test inventory items
        createTestInventoryItem("SKU001", 101L, 1L, 100, 10);
        createTestInventoryItem("SKU002", 102L, 1L, 50, 5);
        createTestInventoryItem("SKU003", 103L, 1L, 0, 5); // Out of stock
    }

    private void createTestStore(Long id, String name, String address, Double lat, Double lon, Integer radius, Boolean active) {
        Store store = Store.builder()
                .id(id)
                .name(name)
                .address(address)
                .latitude(lat)
                .longitude(lon)
                .serviceableRadiusKm(radius)
                .isActive(active)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeRepository.save(store).block();
    }

    @Test
    @DisplayName("Should find nearest store with inventory for serviceable location")
    void shouldFindNearestStoreWithInventory() {
        // Given - Customer location within 5km of store
        Double customerLat = -15.3900; // ~300m from store
        Double customerLon = 28.3250;
        List<String> skus = List.of("SKU001", "SKU002");

        // When
        var result = inventoryService.findNearestStoreWithInventory(customerLat, customerLon, skus);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStoreId()).isEqualTo(1L);
                    assertThat(response.getStoreName()).isEqualTo("Lusaka Dark Store 1");
                    assertThat(response.getDistanceKm()).isLessThan(1.0); // Close to store
                    assertThat(response.getEstimatedDeliveryMinutes()).isBetween(8, 12); // Base time
                    assertThat(response.getIsServiceable()).isTrue();

                    // Verify inventory availability
                    assertThat(response.getInventoryAvailability()).isNotNull();
                    assertThat(response.getInventoryAvailability().getStoreId()).isEqualTo(1L);
                    assertThat(response.getInventoryAvailability().getProducts()).hasSize(2);

                    // Check SKU001 availability
                    var sku001 = response.getInventoryAvailability().getProducts().stream()
                            .filter(p -> "SKU001".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(sku001.getInStock()).isTrue();
                    assertThat(sku001.getAvailableStock()).isEqualTo(100);

                    // Check SKU002 availability
                    var sku002 = response.getInventoryAvailability().getProducts().stream()
                            .filter(p -> "SKU002".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(sku002.getInStock()).isTrue();
                    assertThat(sku002.getAvailableStock()).isEqualTo(50);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return error for location outside serviceable area")
    void shouldReturnErrorForNonServiceableLocation() {
        // Given - Customer location >5km from store
        Double customerLat = -15.5000; // Far from store
        Double customerLon = 28.5000;
        List<String> skus = List.of("SKU001");

        // When
        var result = inventoryService.findNearestStoreWithInventory(customerLat, customerLon, skus);

        // Then
        StepVerifier.create(result)
                .expectError(InventoryNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should include out of stock items in response")
    void shouldIncludeOutOfStockItems() {
        // Given
        Double customerLat = -15.3900;
        Double customerLon = 28.3250;
        List<String> skus = List.of("SKU001", "SKU003"); // SKU003 is out of stock

        // When
        var result = inventoryService.findNearestStoreWithInventory(customerLat, customerLon, skus);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getInventoryAvailability().getProducts()).hasSize(2);

                    // Check SKU003 (out of stock)
                    var sku003 = response.getInventoryAvailability().getProducts().stream()
                            .filter(p -> "SKU003".equals(p.getSku()))
                            .findFirst().orElseThrow();
                    assertThat(sku003.getInStock()).isFalse();
                    assertThat(sku003.getAvailableStock()).isEqualTo(0);
                    assertThat(sku003.getAvailabilityStatus()).isEqualTo("OUT_OF_STOCK");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle SKUs not in inventory")
    void shouldHandleNonExistentSkus() {
        // Given
        Double customerLat = -15.3900;
        Double customerLon = 28.3250;
        List<String> skus = List.of("SKU001", "NON_EXISTENT_SKU");

        // When
        var result = inventoryService.findNearestStoreWithInventory(customerLat, customerLon, skus);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    // Should only return SKU001 (exists in inventory)
                    assertThat(response.getInventoryAvailability().getProducts()).hasSize(1);
                    assertThat(response.getInventoryAvailability().getProducts().get(0).getSku()).isEqualTo("SKU001");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate increasing delivery time with distance")
    void shouldCalculateDeliveryTimeBasedOnDistance() {
        // Given - Nearby location
        Double nearbyLat = -15.3900; // ~300m away
        Double nearbyLon = 28.3250;

        // When
        var nearbyResult = inventoryService.findNearestStoreWithInventory(nearbyLat, nearbyLon, List.of("SKU001"));

        // Then - Should be base time (~8 min)
        StepVerifier.create(nearbyResult)
                .assertNext(response -> {
                    assertThat(response.getEstimatedDeliveryMinutes()).isLessThanOrEqualTo(10);
                })
                .verifyComplete();

        // Given - Farther location (but still within 5km serviceable radius)
        // Store is at -15.3875, 28.3228
        // This location is approximately 4km away (still within 5km radius)
        Double farLat = -15.4200; // South of store
        Double farLon = 28.3300; // Slightly east

        // When
        var farResult = inventoryService.findNearestStoreWithInventory(farLat, farLon, List.of("SKU001"));

        // Then - Should be higher than base time
        StepVerifier.create(farResult)
                .assertNext(response -> {
                    assertThat(response.getDistanceKm()).isLessThanOrEqualTo(5.0); // Within serviceable radius
                    assertThat(response.getEstimatedDeliveryMinutes()).isGreaterThan(10);
                    assertThat(response.getEstimatedDeliveryMinutes()).isLessThanOrEqualTo(25); // Within 25min promise
                })
                .verifyComplete();
    }
}
