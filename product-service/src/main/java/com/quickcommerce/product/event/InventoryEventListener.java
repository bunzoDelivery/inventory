package com.quickcommerce.product.event;

import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.domain.StockMovement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Event listener for inventory-related events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    /**
     * Handle stock movement events
     */
    @EventListener
    public void handleStockMovement(StockMovementEvent event) {
        StockMovement movement = event.getStockMovement();

        // Log the movement
        log.info("Stock movement: {} - {} units for item {} (Ref: {})",
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getInventoryItemId(),
                movement.getReferenceId());

        // TODO: Send to message queue for other services
        // TODO: Update metrics
        // TODO: Trigger notifications if needed
    }

    /**
     * Handle low stock alert events
     */
    @EventListener
    public void handleLowStockAlert(LowStockAlertEvent event) {
        InventoryItem item = event.getInventoryItem();

        // Log the alert
        log.warn("Low stock alert for SKU: {} - Current: {}, Safety: {}, Store: {}",
                item.getSku(),
                item.getCurrentStock(),
                item.getSafetyStock(),
                item.getStoreId());

        // TODO: Send notification to store manager
        // TODO: Update metrics
        // TODO: Trigger reorder process
    }
}
