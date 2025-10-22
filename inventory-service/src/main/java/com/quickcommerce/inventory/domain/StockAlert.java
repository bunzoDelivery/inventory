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
 * Domain entity for stock alert configurations
 * Defines thresholds and notification preferences for low stock alerts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("stock_alerts")
public class StockAlert {

    @Id
    private Long id;

    @Column("inventory_item_id")
    private Long inventoryItemId;

    @Column("alert_threshold")
    private Integer alertThreshold;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private LocalDateTime createdAt;

    /**
     * Check if alert should be triggered for given stock level
     */
    public boolean shouldTriggerAlert(int currentStock) {
        return isActive && currentStock <= alertThreshold;
    }
}
