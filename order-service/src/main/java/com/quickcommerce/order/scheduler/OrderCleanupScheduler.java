package com.quickcommerce.order.scheduler;

import com.quickcommerce.order.client.InventoryClient;
import com.quickcommerce.order.domain.OrderEvent;
import com.quickcommerce.order.domain.OrderStatus;
import com.quickcommerce.order.repository.OrderEventRepository;
import com.quickcommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCleanupScheduler {

    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final InventoryClient inventoryClient;

    @Value("${order.payment-timeout-minutes:15}")
    private int paymentTimeoutMinutes;

    @Scheduled(fixedRateString = "${order.cleanup-interval-ms:60000}")
    public void cancelUnpaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(paymentTimeoutMinutes);

        orderRepo.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT.name(), cutoff)
                .take(50)
                .flatMap(order -> {
                    log.info("Cancelling expired order: {} (created at {})", order.getOrderUuid(), order.getCreatedAt());
                    OrderStatus previous = order.orderStatus();

                    return inventoryClient.cancelOrderReservations(order.getOrderUuid())
                            .onErrorResume(e -> {
                                log.error("Failed to release stock for expired order {}", order.getOrderUuid(), e);
                                return Mono.empty();
                            })
                            .then(Mono.defer(() -> {
                                order.setStatus(OrderStatus.CANCELLED.name());
                                order.setCancelledReason("Payment timeout");
                                return orderRepo.save(order)
                                        .flatMap(saved ->
                                                orderEventRepo.save(
                                                        OrderEvent.cancelled(saved.getId(), previous,
                                                                "Payment timeout after " + paymentTimeoutMinutes + " minutes",
                                                                "SYSTEM")));
                            }));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Cleanup scheduler error", e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}
