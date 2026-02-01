package com.quickcommerce.product.dto;

import com.quickcommerce.product.domain.StockReservation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for stock reservation information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationResponse {

    private Long id;
    private String reservationId;
    private Long inventoryItemId;
    private String sku;
    private Integer quantity;
    private Long customerId;
    private String orderId;
    private LocalDateTime expiresAt;
    private String status;
    private LocalDateTime createdAt;
    private long minutesUntilExpiration;
    private boolean expired;
    private boolean active;

    /**
     * Convert domain entity to response DTO
     */
    public static StockReservationResponse fromDomain(StockReservation reservation, String sku) {
        return StockReservationResponse.builder()
                .id(reservation.getId())
                .reservationId(reservation.getReservationId())
                .inventoryItemId(reservation.getInventoryItemId())
                .sku(sku)
                .quantity(reservation.getQuantity())
                .customerId(reservation.getCustomerId())
                .orderId(reservation.getOrderId())
                .expiresAt(reservation.getExpiresAt())
                .status(reservation.getStatus().name())
                .createdAt(reservation.getCreatedAt())
                .minutesUntilExpiration(reservation.getMinutesUntilExpiration())
                .expired(reservation.isExpired())
                .active(reservation.isActive())
                .build();
    }
}
