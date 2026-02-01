package com.quickcommerce.product.repository;

import com.quickcommerce.product.BaseContainerTest;
import com.quickcommerce.product.domain.Store;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for StoreRepository with geospatial queries in Product
 * Service
 */
class StoreRepositoryIntegrationTest extends BaseContainerTest {

    @Autowired
    private StoreRepository storeRepository;

    @BeforeEach
    void setUpTest() {
        // Store 1 is created by TestDatabaseInitializer (id=1, name='Test Store')
    }

    @Test
    @DisplayName("Should find active stores only")
    void shouldFindActiveStoresOnly() {
        var result = storeRepository.findByIsActive(true);

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find nearest store to given coordinates")
    void shouldFindNearestStore() {
        // Given
        Double customerLat = 0.001;
        Double customerLon = 0.001;

        // When
        var result = storeRepository.findNearestStore(customerLat, customerLon);

        // Then
        StepVerifier.create(result)
                .assertNext(store -> {
                    assertThat(store.getId()).isEqualTo(1L);
                    assertThat(store.getIsActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should find stores within radius")
    void shouldFindStoresWithinRadius() {
        // Given
        Double customerLat = 0.001;
        Double customerLon = 0.001;
        Integer searchRadius = 10;

        // When
        var result = storeRepository.findStoresWithinRadius(customerLat, customerLon, searchRadius);

        // Then
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should check if location is serviceable")
    void shouldCheckLocationServiceability() {
        Double customerLat = 0.001;
        Double customerLon = 0.001;

        var result = storeRepository.isLocationServiceable(customerLat, customerLon);

        StepVerifier.create(result)
                .assertNext(isServiceable -> {
                    assertThat(isServiceable).isTrue();
                })
                .verifyComplete();
    }
}
