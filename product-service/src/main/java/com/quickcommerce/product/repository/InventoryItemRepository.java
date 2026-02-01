package com.quickcommerce.product.repository;

import com.quickcommerce.product.domain.InventoryItem;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * R2DBC repository for inventory items
 */
@Repository
public interface InventoryItemRepository extends ReactiveCrudRepository<InventoryItem, Long> {

    /**
     * Find inventory item by SKU
     */
    Mono<InventoryItem> findBySku(String sku);

    /**
     * Find all inventory items for a specific store
     */
    Flux<InventoryItem> findByStoreId(Long storeId);

    /**
     * Find items with stock below or equal to threshold
     */
    Flux<InventoryItem> findByCurrentStockLessThanEqual(Integer threshold);

    /**
     * Find items with low stock (current stock <= safety stock)
     */
    @Query("SELECT * FROM inventory_items WHERE current_stock <= safety_stock AND store_id = :storeId")
    Flux<InventoryItem> findLowStockItems(Long storeId);

    /**
     * Find available inventory by SKU (current - reserved >= quantity)
     */
    @Query("SELECT * FROM inventory_items WHERE current_stock - reserved_stock >= :quantity AND sku = :sku")
    Mono<InventoryItem> findAvailableBySku(String sku, Integer quantity);

    /**
     * Update current stock with optimistic locking
     */
    @Modifying
    @Query("UPDATE inventory_items SET current_stock = :newStock, version = version + 1, last_updated = CURRENT_TIMESTAMP WHERE id = :id AND version = :version")
    Mono<Integer> updateCurrentStockWithVersion(Long id, Integer newStock, Long version);

    /**
     * Update reserved stock by incrementing (atomic operation)
     */
    @Modifying
    @Query("UPDATE inventory_items SET reserved_stock = reserved_stock + :increment, version = version + 1, last_updated = CURRENT_TIMESTAMP WHERE id = :id")
    Mono<Integer> incrementReservedStock(Long id, Integer increment);

    /**
     * Atomically reserve stock with availability check
     * This prevents race conditions by checking and reserving in ONE atomic operation
     *
     * Returns:
     * - 1 if reservation succeeded (stock was available and reserved)
     * - 0 if reservation failed (insufficient stock)
     *
     * This is the CRITICAL method for preventing overselling in concurrent scenarios
     */
    @Modifying
    @Query("""
        UPDATE inventory_items
        SET reserved_stock = reserved_stock + :quantity,
            version = version + 1,
            last_updated = CURRENT_TIMESTAMP
        WHERE id = :id
          AND (current_stock - reserved_stock) >= :quantity
        """)
    Mono<Integer> reserveStockAtomic(Long id, Integer quantity);

    /**
     * Update both current and reserved stock
     */
    @Modifying
    @Query("UPDATE inventory_items SET current_stock = :newCurrentStock, reserved_stock = :newReservedStock, version = version + 1, last_updated = CURRENT_TIMESTAMP WHERE id = :id AND version = :version")
    Mono<Integer> updateStockWithVersion(Long id, Integer newCurrentStock, Integer newReservedStock, Long version);

    /**
     * Find items that need replenishment
     */
    @Query("SELECT * FROM inventory_items WHERE current_stock <= (safety_stock * 1.5) AND store_id = :storeId")
    Flux<InventoryItem> findItemsNeedingReplenishment(Long storeId);

    /**
     * Find items by product ID
     */
    Flux<InventoryItem> findByProductId(Long productId);

    /**
     * Find items with stock above threshold
     */
    Flux<InventoryItem> findByCurrentStockGreaterThan(Integer threshold);

    /**
     * Find inventory items by store ID and SKU list
     */
    @Query("SELECT * FROM inventory_items WHERE store_id = :storeId AND sku IN (:skus)")
    Flux<InventoryItem> findByStoreIdAndSkuIn(Long storeId, java.util.List<String> skus);

    /**
     * Find inventory item by store ID and SKU
     */
    @Query("SELECT * FROM inventory_items WHERE store_id = :storeId AND sku = :sku")
    Mono<InventoryItem> findByStoreIdAndSku(Long storeId, String sku);
}
