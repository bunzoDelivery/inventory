package com.quickcommerce.order.scheduler;

import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.domain.Order;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

    private final OrderRepository orderRepo;
    private final InventoryClient inventoryClient;

    @Scheduled(fixedRate = 60000) // Every minute
    public void cancelUnpaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

        orderRepo.findByStatusAndCreatedAtBefore("PENDING_PAYMENT", cutoff)
                .take(50)
                .flatMap(order -> {
                    log.info("Cancelling expired order: {}", order.getOrderUuid());
                    return inventoryClient.cancelOrderReservations(order.getOrderUuid())
                            .onErrorResume(e -> {
                                log.error("Failed to cancel reservations for order {}", order.getOrderUuid(), e);
                                return Mono.empty();
                            })
                            .then(Mono.defer(() -> {
                                order.setStatus("CANCELLED");
                                return orderRepo.save(order);
                            }));
                })
                .onErrorResume(e -> {
                    log.error("Cleanup batch failed", e);
                    return Mono.empty();
                })
                .doOnError(e -> log.error("Cleanup scheduler error", e))
                .subscribe();
    }
}
