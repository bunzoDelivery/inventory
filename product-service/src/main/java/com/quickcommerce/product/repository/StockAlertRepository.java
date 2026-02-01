package com.quickcommerce.product.repository;

import com.quickcommerce.product.domain.StockAlert;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC repository for stock alerts
 */
@Repository
public interface StockAlertRepository extends ReactiveCrudRepository<StockAlert, Long> {

    /**
     * Find alert by inventory item ID
     */
    Mono<StockAlert> findByInventoryItemId(Long inventoryItemId);

    /**
     * Find all active alerts
     */
    Flux<StockAlert> findByIsActiveTrue();

    /**
     * Find active alerts for a specific store
     */
    @Query("SELECT sa.* FROM stock_alerts sa " +
            "JOIN inventory_items ii ON sa.inventory_item_id = ii.id " +
            "WHERE sa.is_active = true AND ii.store_id = :storeId")
    Flux<StockAlert> findActiveAlertsByStoreId(Long storeId);

    /**
     * Find alerts with threshold below specified value
     */
    Flux<StockAlert> findByAlertThresholdLessThanEqualAndIsActiveTrue(Integer threshold);

    /**
     * Check if alert exists for inventory item
     */
    @Query("SELECT COUNT(*) > 0 FROM stock_alerts WHERE inventory_item_id = :inventoryItemId")
    Mono<Boolean> existsByInventoryItemId(Long inventoryItemId);
}
