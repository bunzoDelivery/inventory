package com.quickcommerce.product.event;

import com.quickcommerce.product.domain.InventoryItem;
import com.quickcommerce.product.domain.StockMovement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Event listener for inventory-related events
 * RabbitMQ integration is optional - falls back to logging if messaging is disabled
 */
@Component
@Slf4j
public class InventoryEventListener {

    private final Optional<RabbitTemplate> rabbitTemplate;

    public InventoryEventListener(Optional<RabbitTemplate> rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        if (rabbitTemplate.isPresent()) {
            log.info("RabbitMQ messaging enabled for inventory events");
        } else {
            log.info("RabbitMQ messaging disabled - events will be logged only");
        }
    }

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

        // Send to message queue if messaging is enabled
        publishToQueue("stock.movements", event);
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

        // Send to message queue if messaging is enabled
        publishToQueue("stock.alerts", event);
    }

    /**
     * Publish event to RabbitMQ or log if messaging is disabled
     */
    private void publishToQueue(String queueName, Object event) {
        rabbitTemplate.ifPresentOrElse(
            template -> {
                try {
                    template.convertAndSend(queueName, event);
                    log.debug("Published event to queue: {}", queueName);
                } catch (AmqpException e) {
                    log.error("Failed to publish event to queue: {}", queueName, e);
                    // Fallback: Event already logged above
                }
            },
            () -> log.debug("Messaging disabled - event logged only (queue: {})", queueName)
        );
    }
}
