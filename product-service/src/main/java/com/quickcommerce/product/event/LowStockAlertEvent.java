package com.quickcommerce.product.event;

import com.quickcommerce.product.domain.InventoryItem;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when stock falls below safety threshold
 */
@Getter
public class LowStockAlertEvent extends ApplicationEvent {

    private final InventoryItem inventoryItem;

    public LowStockAlertEvent(InventoryItem inventoryItem) {
        super(inventoryItem);
        this.inventoryItem = inventoryItem;
    }
}
