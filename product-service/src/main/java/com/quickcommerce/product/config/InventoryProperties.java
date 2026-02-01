package com.quickcommerce.product.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for inventory management
 * Externalizes hardcoded business logic values to configuration
 */
@Configuration
@ConfigurationProperties(prefix = "inventory")
@Data
@Validated
public class InventoryProperties {

    private Reservation reservation = new Reservation();
    private Stock stock = new Stock();

    /**
     * Stock reservation configuration
     */
    @Data
    public static class Reservation {
        /**
         * Time-to-live for reservations in minutes (5-60 minutes)
         */
        @Min(5)
        @Max(60)
        private int ttlMinutes = 15;

        /**
         * Interval for expired reservation cleanup task in seconds
         */
        @Min(10)
        @Max(600)
        private int cleanupIntervalSeconds = 60;

        /**
         * Batch size for processing expired reservations
         */
        @Min(10)
        @Max(500)
        private int cleanupBatchSize = 50;
    }

    /**
     * Stock management configuration
     */
    @Data
    public static class Stock {
        /**
         * Multiplier for safety stock threshold (e.g., 1.5 means 50% above safety stock)
         */
        @Min(1)
        @Max(3)
        private double safetyStockMultiplier = 1.5;

        /**
         * Default safety stock for new inventory items
         */
        @Min(0)
        private int defaultSafetyStock = 10;

        /**
         * Default maximum stock capacity for new inventory items
         */
        @Min(100)
        private int defaultMaxStock = 1000;
    }
}
