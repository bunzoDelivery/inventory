package com.quickcommerce.inventory.repository;

import com.quickcommerce.inventory.domain.Store;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC repository for store management
 */
@Repository
public interface StoreRepository extends ReactiveCrudRepository<Store, Long> {

    /**
     * Find all active stores
     */
    Flux<Store> findByIsActive(Boolean isActive);

    /**
     * Find stores within a radius of given coordinates
     * Uses Haversine formula for distance calculation
     *
     * @param lat Customer latitude
     * @param lon Customer longitude
     * @param radiusKm Search radius in kilometers
     * @return Stores within radius, ordered by distance
     */
    @Query("""
        SELECT *,
        (6371 * acos(
            cos(radians(:lat)) * cos(radians(latitude)) *
            cos(radians(longitude) - radians(:lon)) +
            sin(radians(:lat)) * sin(radians(latitude))
        )) AS distance_km
        FROM stores
        WHERE is_active = TRUE
        HAVING distance_km <= :radiusKm
        ORDER BY distance_km
    """)
    Flux<Store> findStoresWithinRadius(Double lat, Double lon, Integer radiusKm);

    /**
     * Find nearest active store to given coordinates
     *
     * @param lat Customer latitude
     * @param lon Customer longitude
     * @return Nearest store
     */
    @Query("""
        SELECT *,
        (6371 * acos(
            cos(radians(:lat)) * cos(radians(latitude)) *
            cos(radians(longitude) - radians(:lon)) +
            sin(radians(:lat)) * sin(radians(latitude))
        )) AS distance_km
        FROM stores
        WHERE is_active = TRUE
        ORDER BY distance_km
        LIMIT 1
    """)
    Mono<Store> findNearestStore(Double lat, Double lon);

    /**
     * Check if any store can service given coordinates
     *
     * @param lat Customer latitude
     * @param lon Customer longitude
     * @return true if any store can service this location
     */
    @Query("""
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END AS is_serviceable
        FROM stores
        WHERE is_active = TRUE
        AND (6371 * acos(
            cos(radians(:lat)) * cos(radians(latitude)) *
            cos(radians(longitude) - radians(:lon)) +
            sin(radians(:lat)) * sin(radians(latitude))
        )) <= serviceable_radius_km
    """)
    Mono<Integer> isLocationServiceableInt(Double lat, Double lon);

    /**
     * Helper method to convert Integer result to Boolean
     */
    default Mono<Boolean> isLocationServiceable(Double lat, Double lon) {
        return isLocationServiceableInt(lat, lon)
                .map(result -> result > 0);
    }
}
