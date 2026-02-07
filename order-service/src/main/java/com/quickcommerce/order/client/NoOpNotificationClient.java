package com.quickcommerce.order.client;

import com.quickcommerce.order.domain.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NoOpNotificationClient implements NotificationClient {

    @Override
    public Mono<Void> sendOrderConfirmedEvent(Order order) {
        log.info("stub: Would have sent notification for Order #{}", order.getOrderUuid());
        return Mono.empty();
    }
}
