package com.quickcommerce.order.scheduler;

import com.quickcommerce.order.client.InventoryClient; // Note: We might need release endpoint
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

    private final OrderRepository orderRepo;
    // We haven't implemented releaseStock in InventoryClient yet because InventoryService might not have it exposed directly?
    // Looking at InventoryService plan/code: "InventoryService.reserveStock" creates reservations with TTL.
    // So actually, if we do nothing, the reservation expires in Inventory Service!
    // BUT, we still need to cancel the LOCAL order status so user can't pay for it later.
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void cancelUnpaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        orderRepo.findByStatusAndCreatedAtBefore("PENDING_PAYMENT", cutoff)
                .flatMap(order -> {
                    log.info("Cancelling expired order: {}", order.getOrderUuid());
                    order.setStatus("CANCELLED");
                    return orderRepo.save(order);
                })
                .subscribe();
    }
}
