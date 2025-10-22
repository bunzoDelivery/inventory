package com.quickcommerce.inventory.event;

import com.quickcommerce.inventory.domain.StockMovement;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a stock movement occurs
 */
@Getter
public class StockMovementEvent extends ApplicationEvent {

    private final StockMovement stockMovement;

    public StockMovementEvent(StockMovement stockMovement) {
        super(stockMovement);
        this.stockMovement = stockMovement;
    }
}
