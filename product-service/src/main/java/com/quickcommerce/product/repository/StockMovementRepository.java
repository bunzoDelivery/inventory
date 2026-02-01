package com.quickcommerce.product.repository;

import com.quickcommerce.product.domain.StockMovement;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

/**
 * R2DBC repository for stock movements
 */
@Repository
public interface StockMovementRepository extends ReactiveCrudRepository<StockMovement, Long> {

    /**
     * Find movements for a specific inventory item, ordered by creation date
     */
    Flux<StockMovement> findByInventoryItemIdOrderByCreatedAtDesc(Long inventoryItemId);

    /**
     * Find movements by reference ID
     */
    Flux<StockMovement> findByReferenceId(String referenceId);

    /**
     * Find movements within date range
     */
    @Query("SELECT * FROM inventory_movements WHERE created_at >= :fromDate AND created_at <= :toDate ORDER BY created_at DESC")
    Flux<StockMovement> findByDateRange(LocalDateTime fromDate, LocalDateTime toDate);

    /**
     * Find movements by type
     */
    Flux<StockMovement> findByMovementType(StockMovement.MovementType movementType);

    /**
     * Find movements by reference type
     */
    Flux<StockMovement> findByReferenceType(StockMovement.ReferenceType referenceType);

    /**
     * Find movements for a specific inventory item and movement type
     */
    Flux<StockMovement> findByInventoryItemIdAndMovementType(Long inventoryItemId,
            StockMovement.MovementType movementType);

    /**
     * Find movements created by a specific user
     */
    Flux<StockMovement> findByCreatedBy(String createdBy);

    /**
     * Find recent movements (last 24 hours)
     */
    @Query("SELECT * FROM inventory_movements WHERE created_at >= :since ORDER BY created_at DESC")
    Flux<StockMovement> findRecentMovements(LocalDateTime since);
}
