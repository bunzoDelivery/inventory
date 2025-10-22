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
 * Domain entity representing stock reservations for checkout process
 * Temporarily holds stock for customers during order completion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("stock_reservations")
public class StockReservation {

    @Id
    private Long id;

    @Column("reservation_id")
    private String reservationId;

    @Column("inventory_item_id")
    private Long inventoryItemId;

    @Column("quantity")
    private Integer quantity;

    @Column("customer_id")
    private Long customerId;

    @Column("order_id")
    private String orderId;

    @Column("expires_at")
    private LocalDateTime expiresAt;

    @Column("status")
    private ReservationStatus status;

    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Reservation status lifecycle
     */
    public enum ReservationStatus {
        ACTIVE("Active reservation"),
        CONFIRMED("Reservation confirmed as sale"),
        EXPIRED("Reservation expired"),
        CANCELLED("Reservation cancelled");

        private final String description;

        ReservationStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Check if reservation is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if reservation is active
     */
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE && !isExpired();
    }

    /**
     * Get time remaining until expiration in minutes
     */
    public long getMinutesUntilExpiration() {
        if (isExpired()) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
    }
}
