package com.quickcommerce.inventory.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain entity representing an inventory item
 * Tracks stock levels, reservations, and safety stock for each SKU
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("inventory_items")
public class InventoryItem {

    @Id
    private Long id;

    @Column("sku")
    private String sku;

    @Column("product_id")
    private Long productId;

    @Column("store_id")
    private Long storeId;

    @Column("current_stock")
    private Integer currentStock;

    @Column("reserved_stock")
    private Integer reservedStock;

    @Column("safety_stock")
    private Integer safetyStock;

    @Column("max_stock")
    private Integer maxStock;

    @Column("unit_cost")
    private BigDecimal unitCost;

    @Version
    @Column("version")
    private Long version;

    @Column("last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Check if sufficient stock is available for reservation
     */
    public boolean isAvailableForReservation(int quantity) {
        return getAvailableStock() >= quantity;
    }

    /**
     * Get currently available stock (current - reserved)
     */
    public int getAvailableStock() {
        return currentStock - reservedStock;
    }

    /**
     * Check if stock is below safety threshold
     */
    public boolean isLowStock() {
        return currentStock <= safetyStock;
    }

    /**
     * Check if stock needs replenishment
     */
    public boolean needsReplenishment() {
        return currentStock <= safetyStock * 1.5; // 50% above safety stock
    }

    /**
     * Calculate suggested reorder quantity
     */
    public int getSuggestedReorderQuantity() {
        return Math.max(maxStock - currentStock, safetyStock * 2);
    }
}
