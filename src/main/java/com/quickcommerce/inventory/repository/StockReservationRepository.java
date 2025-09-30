package com.quickcommerce.inventory.repository;

import com.quickcommerce.inventory.domain.StockReservation;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * R2DBC repository for stock reservations
 */
@Repository
public interface StockReservationRepository extends ReactiveCrudRepository<StockReservation, Long> {

    /**
     * Find reservation by reservation ID
     */
    Mono<StockReservation> findByReservationId(String reservationId);

    /**
     * Find reservations for a specific customer
     */
    Flux<StockReservation> findByCustomerId(Long customerId);

    /**
     * Find reservations for a specific order
     */
    Flux<StockReservation> findByOrderId(String orderId);

    /**
     * Find reservations for a specific inventory item
     */
    Flux<StockReservation> findByInventoryItemId(Long inventoryItemId);

    /**
     * Find active reservations that have expired
     */
    @Query("SELECT * FROM stock_reservations WHERE status = 'ACTIVE' AND expires_at < :expiresAt")
    Flux<StockReservation> findByStatusAndExpiresAtBefore(StockReservation.ReservationStatus status,
            LocalDateTime expiresAt);

    /**
     * Find active reservations
     */
    Flux<StockReservation> findByStatus(StockReservation.ReservationStatus status);

    /**
     * Find reservations expiring soon (within specified minutes)
     */
    @Query("SELECT * FROM stock_reservations WHERE status = 'ACTIVE' AND expires_at BETWEEN :now AND :expiresSoon")
    Flux<StockReservation> findReservationsExpiringSoon(LocalDateTime now, LocalDateTime expiresSoon);

    /**
     * Update reservation status
     */
    @Modifying
    @Query("UPDATE stock_reservations SET status = :status WHERE reservation_id = :reservationId")
    Mono<Integer> updateStatus(String reservationId, StockReservation.ReservationStatus status);

    /**
     * Update reservation status by ID
     */
    @Modifying
    @Query("UPDATE stock_reservations SET status = :status WHERE id = :id")
    Mono<Integer> updateStatusById(Long id, StockReservation.ReservationStatus status);

    /**
     * Find reservations created after a specific date
     */
    Flux<StockReservation> findByCreatedAtAfter(LocalDateTime createdAt);

    /**
     * Find reservations by inventory item and status
     */
    Flux<StockReservation> findByInventoryItemIdAndStatus(Long inventoryItemId,
            StockReservation.ReservationStatus status);

    /**
     * Count active reservations for a customer
     */
    @Query("SELECT COUNT(*) FROM stock_reservations WHERE customer_id = :customerId AND status = 'ACTIVE'")
    Mono<Long> countActiveReservationsByCustomerId(Long customerId);
}
