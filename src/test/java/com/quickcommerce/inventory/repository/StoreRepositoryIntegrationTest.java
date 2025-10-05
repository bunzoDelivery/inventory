package com.quickcommerce.inventory.repository;

import com.quickcommerce.inventory.BaseContainerTest;
import com.quickcommerce.inventory.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for StoreRepository with geospatial queries
 */
class StoreRepositoryIntegrationTest extends BaseContainerTest {

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void setUpTest() {
        // Store 1 is already created by TestDatabaseInitializer (id=1)
        // We don't need to recreate it in tests since tests inherit from BaseContainerTest
    }

    @Test
    @DisplayName("Should find active stores only")
    void shouldFindActiveStoresOnly() {
        // When
        var result = storeRepository.findByIsActive(true);

        // Then
        StepVerifier.create(result)
                .expectNextCount(1) // Only 1 active store from TestDatabaseInitializer
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find nearest store to given coordinates")
    void shouldFindNearestStore() {
        // Given - Customer location near Store 1
        Double customerLat = -15.3900; // Close to Store 1 (-15.3875)
        Double customerLon = 28.3250;  // Close to Store 1 (28.3228)

        // When
        var result = storeRepository.findNearestStore(customerLat, customerLon);

        // Then
        StepVerifier.create(result)
                .assertNext(store -> {
                    assertThat(store.getId()).isEqualTo(1L);
                    assertThat(store.getName()).isEqualTo("Lusaka Dark Store 1");
                    assertThat(store.getIsActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find stores within radius")
    void shouldFindStoresWithinRadius() {
        // Given - Customer location
        Double customerLat = -15.3900;
        Double customerLon = 28.3250;
        Integer searchRadius = 10; // 10 km radius

        // When
        var result = storeRepository.findStoresWithinRadius(customerLat, customerLon, searchRadius);

        // Then - One active store should be within 10km
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if location is serviceable")
    void shouldCheckLocationServiceability() {
        // Given - Customer location close to Store 1 (within 5km)
        Double customerLat = -15.3900;
        Double customerLon = 28.3250;

        // When
        var result = storeRepository.isLocationServiceable(customerLat, customerLon);

        // Then
        StepVerifier.create(result)
                .assertNext(isServiceable -> {
                    assertThat(isServiceable).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return false for location outside serviceable area")
    void shouldReturnFalseForNonServiceableLocation() {
        // Given - Customer location far from any store (>5km)
        Double customerLat = -15.5000; // Very far south
        Double customerLon = 28.5000;  // Very far east

        // When
        var result = storeRepository.isLocationServiceable(customerLat, customerLon);

        // Then
        StepVerifier.create(result)
                .assertNext(isServiceable -> {
                    assertThat(isServiceable).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate distance correctly using Haversine formula")
    void shouldCalculateDistanceCorrectly() {
        // Given
        Store store = Store.builder()
                .latitude(-15.3875)
                .longitude(28.3228)
                .build();

        // When - Same location (should be ~0 km)
        double distanceSameLocation = store.calculateDistanceKm(-15.3875, 28.3228);

        // Then
        assertThat(distanceSameLocation).isLessThan(0.01); // Very close to 0

        // When - Known distance (~3 km away)
        double distanceKnown = store.calculateDistanceKm(-15.4000, 28.3300);

        // Then - Should be approximately 1.5-2 km
        assertThat(distanceKnown).isBetween(1.0, 3.0);
    }

    @Test
    @DisplayName("Should determine if location is serviceable")
    void shouldDetermineLocationServiceability() {
        // Given
        Store store = Store.builder()
                .latitude(-15.3875)
                .longitude(28.3228)
                .serviceableRadiusKm(5)
                .build();

        // When - Location within 5km
        boolean nearbyServiceable = store.isLocationServiceable(-15.3900, 28.3250);

        // Then
        assertThat(nearbyServiceable).isTrue();

        // When - Location far away (>5km)
        boolean farServiceable = store.isLocationServiceable(-15.5000, 28.5000);

        // Then
        assertThat(farServiceable).isFalse();
    }

    @Test
    @DisplayName("Should estimate delivery time based on distance")
    void shouldEstimateDeliveryTime() {
        // Given
        Store store = Store.builder()
                .latitude(-15.3875)
                .longitude(28.3228)
                .build();

        // When - Same location (0 km)
        int deliveryTimeSame = store.estimateDeliveryTimeMinutes(-15.3875, 28.3228);

        // Then - Base time should be ~8 minutes
        assertThat(deliveryTimeSame).isEqualTo(8);

        // When - ~3 km away
        int deliveryTimeFar = store.estimateDeliveryTimeMinutes(-15.4100, 28.3400);

        // Then - Should be base (8) + additional time (3 min/km for ~1 km beyond 2km base) = ~11 minutes
        assertThat(deliveryTimeFar).isBetween(10, 15);
    }
}
