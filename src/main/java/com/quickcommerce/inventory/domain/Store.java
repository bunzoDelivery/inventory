package com.quickcommerce.inventory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Domain entity representing a dark store location
 * Stores geospatial data for delivery radius calculations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("stores")
public class Store {

    @Id
    private Long id;

    @Column("name")
    private String name;

    @Column("address")
    private String address;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("serviceable_radius_km")
    private Integer serviceableRadiusKm;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Calculate distance from this store to a given location using Haversine formula
     * @param targetLat Target latitude
     * @param targetLon Target longitude
     * @return Distance in kilometers
     */
    public double calculateDistanceKm(double targetLat, double targetLon) {
        final int EARTH_RADIUS_KM = 6371;

        double latDistance = Math.toRadians(targetLat - this.latitude);
        double lonDistance = Math.toRadians(targetLon - this.longitude);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.latitude)) * Math.cos(Math.toRadians(targetLat))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Check if a given location is within the serviceable radius
     * @param targetLat Target latitude
     * @param targetLon Target longitude
     * @return true if location is serviceable
     */
    public boolean isLocationServiceable(double targetLat, double targetLon) {
        double distance = calculateDistanceKm(targetLat, targetLon);
        return distance <= this.serviceableRadiusKm;
    }

    /**
     * Estimate delivery time in minutes based on distance
     * Assumption: 2km takes ~8 minutes base time + 3 min per additional km
     * @param targetLat Target latitude
     * @param targetLon Target longitude
     * @return Estimated delivery time in minutes
     */
    public int estimateDeliveryTimeMinutes(double targetLat, double targetLon) {
        double distanceKm = calculateDistanceKm(targetLat, targetLon);

        // Base time: 8 minutes for first 2km
        // Additional time: 3 minutes per km beyond 2km
        int baseTime = 8;
        int additionalTime = (int) Math.ceil(Math.max(0, distanceKm - 2) * 3);

        return baseTime + additionalTime;
    }
}
